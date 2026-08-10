package org.example.all_my_trip_project.domain.accommodation.dto;

import java.util.List;

/** 가격 공급자가 목록에 요금을 보강한 결과. */
public record AccommodationPriceResult(
        List<AccommodationOffer> offers,
        int matchedCount
) {
    public static AccommodationPriceResult unchanged(List<AccommodationOffer> offers) {
        return new AccommodationPriceResult(offers, 0);
    }
}
