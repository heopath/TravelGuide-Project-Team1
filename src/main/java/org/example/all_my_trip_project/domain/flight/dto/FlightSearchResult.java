package org.example.all_my_trip_project.domain.flight.dto;

import java.util.List;

/**
 * Composite 조회 결과.
 *
 * @param scheduleProvider  목록을 만든 소스
 * @param priceProvider     가격을 덮어쓴 소스. 붙지 않았으면 null
 * @param matchedPriceCount PRICE provider가 실제로 덮어쓴 건수
 */
public record FlightSearchResult(
        List<FlightOffer> offers,
        String scheduleProvider,
        String priceProvider,
        int matchedPriceCount
) {
    public static FlightSearchResult empty() {
        return new FlightSearchResult(List.of(), null, null, 0);
    }

    public int totalCount() {
        return offers.size();
    }
}
