package org.example.all_my_trip_project.domain.flight.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 항공편 검색 조건.
 *
 * @param origin          출발지 IATA 코드. "ICN"
 * @param destination     도착지 IATA 코드. "CJU"
 * @param departureDate   출발일
 * @param adults          성인 인원
 * @param nonStopOnly     직항만 조회할지 여부
 * @param currency        통화 코드. "KRW"
 * @param firstPlanStartAt 1일차 첫 활동 시작 시각. nullable
 * @param lastPlanEndAt    마지막날 마지막 활동 종료 시각. nullable
 *
 * <p>뒤의 두 값은 일정 충돌 배지와 추천 스코어의 일정 적합도를 계산하는 기준이다.
 * 가는 편을 조회할 때는 {@code firstPlanStartAt}만, 오는 편에는 {@code lastPlanEndAt}만 넘긴다.
 * 둘 다 없으면 일정 페널티가 0이라 사실상 가격만으로 순위가 갈린다.
 */
public record FlightSearchQuery(
        String origin,
        String destination,
        LocalDate departureDate,
        int adults,
        boolean nonStopOnly,
        String currency,
        LocalDateTime firstPlanStartAt,
        LocalDateTime lastPlanEndAt
) {
    public static final String DEFAULT_CURRENCY = "KRW";

    public FlightSearchQuery {
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (adults < 1) {
            adults = 1;
        }
    }

    public String route() {
        return origin + "-" + destination;
    }
}
