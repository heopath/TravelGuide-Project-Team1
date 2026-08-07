package org.example.all_my_trip_project.domain.flight.provider;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;

import java.util.List;

/**
 * 항공편 데이터 소스.
 *
 * <p>단일 provider가 목록과 가격을 다 주지 않는다.
 * {@link ProviderRole#SCHEDULE}이 목록을 만들고 {@link ProviderRole#PRICE}가 가격만 덮어쓴다.
 */
public interface FlightSearchProvider {

    /** "mock" | "tago" | "travelpayouts" — offerId 접두어와 로그에 그대로 쓰인다. */
    String name();

    ProviderRole role();

    /** 노선 커버리지 판정. 거짓이면 호출되지 않는다. */
    boolean supports(FlightSearchQuery query);

    List<FlightOffer> search(FlightSearchQuery query);
}
