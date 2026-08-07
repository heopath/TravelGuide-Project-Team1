package org.example.all_my_trip_project.domain.flight.type;

/**
 * 소스의 역할.
 *
 * <p>단일 provider가 목록과 가격을 다 주지 않는다. TAGO는 스케줄을 주고,
 * Travelpayouts는 캐시된 가격만 소수 준다. 그래서 역할을 나눈다.
 */
public enum ProviderRole {

    /** 항공편 목록을 만든다. TAGO, Mock. */
    SCHEDULE,

    /** 이미 만들어진 목록의 가격을 덮어쓴다. Travelpayouts. */
    PRICE
}
