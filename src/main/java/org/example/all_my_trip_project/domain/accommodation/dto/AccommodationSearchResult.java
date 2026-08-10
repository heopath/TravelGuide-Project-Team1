package org.example.all_my_trip_project.domain.accommodation.dto;

import java.util.List;

/**
 * Composite 조회 결과.
 *
 * @param listingProvider   목록을 만든 소스
 * @param priceProvider     요금을 덮어쓴 소스. 붙지 않았으면 null
 * @param matchedPriceCount PRICE provider가 실제로 덮어쓴 건수
 */
public record AccommodationSearchResult(
        List<AccommodationOffer> offers,
        String listingProvider,
        String priceProvider,
        int matchedPriceCount
) {
    public static AccommodationSearchResult empty() {
        return new AccommodationSearchResult(List.of(), null, null, 0);
    }

    public int totalCount() {
        return offers.size();
    }
}
