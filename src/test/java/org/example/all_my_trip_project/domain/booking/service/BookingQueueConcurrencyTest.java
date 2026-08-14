package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 명이 같은 시간대를 동시에 예약할 때 재고가 넘지 않는지 확인한다.
 *
 * <p><b>부하 도구로는 이걸 못 잡는다.</b> k6는 초당 몇 건을 처리했는지 알려주지만
 * "두 사람이 같은 자리를 받았는가"는 알려주지 않는다. 그건 여기서 본다.
 *
 * <p>실제 DB가 필요하다. 저장소에 testcontainers도 H2도 없어 로컬 PostgreSQL·Redis가 떠
 * 있을 때만 돈다. 켜려면 환경변수를 준다.
 *
 * <pre>
 * BOOKING_QUEUE_CONCURRENCY_TEST=true ./gradlew test --tests "*BookingQueueConcurrencyTest*"
 * </pre>
 *
 * <p><b>운영 DB에 연결한 채로 돌리지 않는다.</b> 임시 데이터를 만들고 지우지만, 같은 시간대에
 * 다른 사람이 예약하면 그 예약까지 경쟁 대상이 되고 재고가 실제로 깎인다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.docker.compose.enabled=false"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "BOOKING_QUEUE_CONCURRENCY_TEST", matches = "true")
class BookingQueueConcurrencyTest {

    /** 재고보다 넉넉히 많이 몰아야 경쟁이 생긴다. 재고와 같으면 전부 성공해도 통과해버린다. */
    private static final int CONTENDERS = 30;
    private static final int STOCK = 10;

    @Autowired
    private BookingQueueService bookingQueueService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long placeId;
    private Long productId;
    private Long optionId;
    private Long slotId;
    private List<Long> tripIds;

    @AfterEach
    void tearDown() {
        if (slotId != null) {
            jdbcTemplate.update("""
                    delete from reservation_items where reservation_id in (
                        select reservation_id from reservations where user_id = ?
                    )
                    """, userId);
            jdbcTemplate.update("delete from reservations where user_id = ?", userId);
            jdbcTemplate.update("delete from ticket_inventory where ticket_time_slot_id = ?", slotId);
            jdbcTemplate.update("delete from ticket_time_slots where ticket_time_slot_id = ?", slotId);
        }
        if (optionId != null) {
            jdbcTemplate.update("delete from ticket_product_options where ticket_product_option_id = ?", optionId);
        }
        if (productId != null) {
            jdbcTemplate.update("delete from ticket_products where ticket_product_id = ?", productId);
        }
        if (tripIds != null) {
            tripIds.forEach(id -> jdbcTemplate.update("delete from trips where trip_id = ?", id));
        }
        if (placeId != null) {
            jdbcTemplate.update("delete from places where place_id = ?", placeId);
        }
        if (userId != null) {
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("재고 10개에 30명이 동시에 몰려도 예약은 10건을 넘지 않는다")
    void neverOversellsUnderConcurrency() throws Exception {
        createFixture();

        CountDownLatch ready = new CountDownLatch(CONTENDERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONTENDERS)) {
            List<Future<?>> futures = tripIds.stream()
                    .<Future<?>>map(tripId -> executor.submit(() -> {
                        /* 모든 스레드를 한 지점에 모았다가 동시에 푼다. 그래야 경쟁이 재현된다. */
                        ready.countDown();
                        try {
                            start.await();
                            reserveOnce(tripId);
                            succeeded.incrementAndGet();
                        } catch (Exception exception) {
                            rejected.incrementAndGet();
                        }
                    }))
                    .toList();

            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        Integer reservedQuantity = jdbcTemplate.queryForObject(
                "select reserved_quantity from ticket_inventory where ticket_time_slot_id = ?",
                Integer.class, slotId);
        Integer reservationRows = jdbcTemplate.queryForObject(
                "select count(*) from reservations where user_id = ? and status = 'PENDING'",
                Integer.class, userId);

        /* 핵심 단정. 재고를 한 건이라도 넘겼다면 잠금이 새고 있다는 뜻이다. */
        assertThat(reservedQuantity).isNotNull().isLessThanOrEqualTo(STOCK);
        assertThat(succeeded.get()).isLessThanOrEqualTo(STOCK);

        /* 성공한 요청 수와 실제로 저장된 예약 수가 어긋나면 응답과 데이터가 다른 것이다. */
        assertThat(reservationRows).isEqualTo(succeeded.get());
        assertThat(reservedQuantity).isEqualTo(succeeded.get());
        assertThat(succeeded.get() + rejected.get()).isEqualTo(CONTENDERS);
    }

    /** 대기열을 거쳐 한 건 예약한다. 차례가 올 때까지 짧게 재시도한다. */
    private void reserveOnce(Long tripId) throws Exception {
        var entry = bookingQueueService.enqueue(userId,
                new CreateTicketReservationRequest(tripId, slotId, 1, UUID.randomUUID().toString()));

        String token = entry.token();
        for (int attempt = 0; attempt < 60; attempt++) {
            var status = bookingQueueService.status(userId, token);
            if (status.status().name().equals("READY")) {
                bookingQueueService.complete(userId, token);
                return;
            }
            if (status.status().name().equals("EXPIRED")) {
                throw new IllegalStateException("대기표 만료");
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("차례가 오지 않음");
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, nickname, role)
                values (?, 'x', ?, 'USER') returning user_id
                """, Long.class, "queue-" + suffix + "@example.com", "queue-" + suffix);

        placeId = jdbcTemplate.queryForObject("""
                insert into places (category, name, country_code, is_active)
                values ('ACTIVITY', ?, 'KR', true) returning place_id
                """, Long.class, "부하테스트 장소 " + suffix);

        productId = jdbcTemplate.queryForObject("""
                insert into ticket_products
                    (place_id, name, sale_start_at, sale_end_at, usage_start_date, usage_end_date, status)
                values (?, ?, now() - interval '1 day', now() + interval '30 day',
                        current_date, current_date + 30, 'ON_SALE')
                returning ticket_product_id
                """, Long.class, placeId, "부하테스트 상품 " + suffix);

        optionId = jdbcTemplate.queryForObject("""
                insert into ticket_product_options
                    (ticket_product_id, name, unit_price, max_quantity_per_user, sort_order, is_active)
                values (?, '성인', 10000, 10, 1, true)
                returning ticket_product_option_id
                """, Long.class, productId);

        slotId = jdbcTemplate.queryForObject("""
                insert into ticket_time_slots (ticket_product_option_id, usage_date, start_time, status)
                values (?, current_date + 1, '10:00', 'OPEN')
                returning ticket_time_slot_id
                """, Long.class, optionId);

        jdbcTemplate.update("""
                insert into ticket_inventory (ticket_time_slot_id, total_quantity, reserved_quantity)
                values (?, ?, 0)
                """, slotId, STOCK);

        /*
         * 여행을 사람 수만큼 만든다. 예약은 여행 소유자만 할 수 있고 requestKey가 같으면
         * 서버가 같은 요청으로 보고 기존 예약을 돌려주므로, 한 여행으로 30번 부르면 경쟁이 생기지 않는다.
         */
        tripIds = java.util.stream.IntStream.range(0, CONTENDERS)
                .mapToObj(index -> jdbcTemplate.queryForObject("""
                        insert into trips (user_id, title, destination_name, start_date, end_date, status)
                        values (?, ?, '부하테스트', ?, ?, 'DRAFT') returning trip_id
                        """, Long.class, userId, "부하테스트 여행 " + index,
                        LocalDate.now(), LocalDate.now().plusDays(30)))
                .toList();
    }
}
