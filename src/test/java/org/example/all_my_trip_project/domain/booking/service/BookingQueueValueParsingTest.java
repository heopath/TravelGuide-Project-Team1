package org.example.all_my_trip_project.domain.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기열이 Redis에 값을 넣고 꺼내는 과정에서 "없음"이 살아남는지 본다.
 *
 * <p>여행 없이 티켓을 사는 길이 생기면서(#255) {@code tripId}가 실제로 없는 요청이
 * 들어오게 됐다. 그런데 인자를 {@code String.valueOf}로 만들면 null이 문자열
 * {@code "null"}이 되어 Redis에 저장되고, 꺼낼 때 0으로 되살아났다. 0은 어느 여행도
 * 아니라 예약이 "여행을 찾을 수 없습니다"로 막혔다.
 *
 * <p>실제 Redis를 띄우는 시험은 {@code RedisBookingQueueStoreIntegrationTest}가 맡지만
 * 환경 변수가 있어야 돌아간다. 이 시험은 평소 빌드에서도 돌면서 값 변환만 본다.
 */
class BookingQueueValueParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("null 인자는 문자열 \"null\"이 아니라 빈 문자열로 넘긴다")
    void nullArgumentBecomesEmptyString() {
        assertThat(RedisBookingQueueStore.argument(null)).isEmpty();
        /* String.valueOf(null)이면 "null"이 된다. 그것이 이번 사고의 시작이었다. */
        assertThat(RedisBookingQueueStore.argument(null)).isNotEqualTo("null");
    }

    @Test
    @DisplayName("값이 있는 인자는 그대로 문자열이 된다")
    void keepsRealArguments() {
        assertThat(RedisBookingQueueStore.argument(50L)).isEqualTo("50");
        assertThat(RedisBookingQueueStore.argument("abc")).isEqualTo("abc");
        assertThat(RedisBookingQueueStore.argument(0L)).isEqualTo("0");
    }

    @Test
    @DisplayName("빈 값은 0이 아니라 null로 읽는다")
    void readsMissingValueAsNull() {
        assertThat(RedisBookingQueueStore.nullableLong(json("{}"), "tripId")).isNull();
        assertThat(RedisBookingQueueStore.nullableLong(json("{\"tripId\":null}"), "tripId")).isNull();
        assertThat(RedisBookingQueueStore.nullableLong(json("{\"tripId\":\"\"}"), "tripId")).isNull();
    }

    @Test
    @DisplayName("예전에 저장된 문자열 \"null\"도 없는 값으로 읽는다")
    void readsLegacyNullTextAsNull() {
        /*
         * 고치기 전에 들어간 항목이 Redis에 남아 있고 TTL이 지나기 전까지 계속 읽힌다.
         * 이걸 안 걸러내면 배포 직후에도 같은 증상이 이어진다.
         */
        assertThat(RedisBookingQueueStore.nullableLong(json("{\"tripId\":\"null\"}"), "tripId")).isNull();
    }

    @Test
    @DisplayName("실제 여행 번호는 그대로 읽는다")
    void readsRealTripId() {
        assertThat(RedisBookingQueueStore.nullableLong(json("{\"tripId\":\"50\"}"), "tripId")).isEqualTo(50L);
        assertThat(RedisBookingQueueStore.nullableLong(json("{\"tripId\":50}"), "tripId")).isEqualTo(50L);
    }
}
