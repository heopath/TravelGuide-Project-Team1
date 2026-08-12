package org.example.all_my_trip_project.domain.accommodation.provider;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationPriceResult;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;

import java.util.List;

/** 숙소 목록의 정보는 유지하고 가격·취소 조건만 보강하는 공급자. */
public interface AccommodationPriceProvider {

    String name();

    boolean supports(AccommodationSearchQuery query, List<AccommodationOffer> offers);

    AccommodationPriceResult apply(List<AccommodationOffer> offers, AccommodationSearchQuery query);
}
