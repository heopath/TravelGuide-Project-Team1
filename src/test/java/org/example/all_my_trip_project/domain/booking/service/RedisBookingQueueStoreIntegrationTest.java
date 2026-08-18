package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.booking.config.BookingQueueProperties;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueState;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** BOOKING_QUEUE_REDIS_INTEGRATION_TEST=true일 때 실제 Redis에서 Lua 원자성을 검증한다. */
@EnabledIfEnvironmentVariable(named = "BOOKING_QUEUE_REDIS_INTEGRATION_TEST", matches = "true")
class RedisBookingQueueStoreIntegrationTest {

    private static final String PREFIX = "all-my-trips:booking-queue:";

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisBookingQueueStore store;
    private long slotId;
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                environmentOrDefault("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(environmentOrDefault("SPRING_DATA_REDIS_PORT", "6379")));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }

        BookingQueueProperties properties = new BookingQueueProperties();
        properties.setCapacityPerSecond(2);
        properties.setEntryTtl(Duration.ofMinutes(10));
        properties.setAdmissionTtl(Duration.ofMinutes(2));
        store = new RedisBookingQueueStore(redisTemplate, properties);
        slotId = 9_000_000L + System.nanoTime() % 1_000_000L;
    }

    @AfterEach
    void tearDown() {
        Set<String> slotKeys = redisTemplate.keys(PREFIX + "slot:" + slotId + "*");
        if (slotKeys != null && !slotKeys.isEmpty()) redisTemplate.delete(slotKeys);
        redisTemplate.delete(tokens.stream().map(token -> PREFIX + "entry:" + token).toList());
        connectionFactory.destroy();
    }

    @Test
    void admitsOnlyConfiguredCapacityAndKeepsOneEntryPerUser() throws Exception {
        Instant now = Instant.now();
        int requestCount = 12;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            var futures = new ArrayList<java.util.concurrent.Future<BookingQueueStatusResponse>>();
            for (int index = 0; index < requestCount; index++) {
                long userId = index + 1L;
                String token = "%032x".formatted(index + 1);
                tokens.add(token);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return store.enqueue(userId,
                            new CreateTicketReservationRequest(100L + userId, slotId, 1, "load-" + userId),
                            token, now);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<BookingQueueStatusResponse> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            assertThat(results).filteredOn(result -> result.status() == BookingQueueState.READY).hasSize(2);
            assertThat(results).filteredOn(result -> result.status() == BookingQueueState.WAITING).hasSize(10);

            BookingQueueStatusResponse waiting = results.stream()
                    .filter(result -> result.status() == BookingQueueState.WAITING).findFirst().orElseThrow();
            BookingQueueStatusResponse duplicate = store.enqueue(
                    waiting.tripId() - 100L,
                    new CreateTicketReservationRequest(waiting.tripId(), slotId, 1, "different-request"),
                    "f".repeat(32), now);
            assertThat(duplicate.token()).isEqualTo(waiting.token());
        }
    }

    /**
     * 여행 없는 요청이 Redis를 왕복해도 {@code tripId}가 null로 남는지 본다.
     *
     * <p>넣을 때 {@code String.valueOf(null)}이 문자열 {@code "null"}을 만들고, 꺼낼 때
     * {@code asLong()}이 그것을 0으로 되살려서, 여행 없이 산 티켓이 "여행을 찾을 수 없습니다"로
     * 막혔다. 0은 어느 여행도 아니다.
     *
     * <p>값 변환만 보는 시험({@code BookingQueueValueParsingTest})과 달리 실제 Lua·Redis를
     * 거친다. 저장 형식이 바뀌어도 여기서 잡힌다.
     */
    @Test
    void keepsTripIdNullThroughRedisRoundTrip() {
        Instant now = Instant.now();
        String token = "a".repeat(32);
        tokens.add(token);

        BookingQueueStatusResponse enqueued = store.enqueue(
                1L, new CreateTicketReservationRequest(null, slotId, 1, "no-trip"), token, now);

        /* 응답에 0이 실리면 화면도 없는 여행을 고른 것처럼 보인다. */
        assertThat(enqueued.tripId()).isNull();
        assertThat(enqueued.status()).isEqualTo(BookingQueueState.READY);

        assertThat(store.status(1L, token, now).tripId()).isNull();

        var claim = store.claim(1L, token, now);
        assertThat(claim.claimed()).isTrue();
        /* 여기가 핵심. 0이면 requireOwnedTrip이 TRIP_NOT_FOUND로 막는다. */
        assertThat(claim.request().tripId()).isNull();
        assertThat(claim.request().slotId()).isEqualTo(slotId);
        assertThat(claim.request().quantity()).isEqualTo(1);
        assertThat(claim.request().requestKey()).isEqualTo("no-trip");
    }

    /** 여행을 고른 경우는 그대로 살아남아야 한다. 없는 값만 걸러내는 것이지 전부 비우는 게 아니다. */
    @Test
    void keepsRealTripIdThroughRedisRoundTrip() {
        Instant now = Instant.now();
        String token = "b".repeat(32);
        tokens.add(token);

        BookingQueueStatusResponse enqueued = store.enqueue(
                2L, new CreateTicketReservationRequest(50L, slotId, 2, "with-trip"), token, now);

        assertThat(enqueued.tripId()).isEqualTo(50L);
        assertThat(store.claim(2L, token, now).request().tripId()).isEqualTo(50L);
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
