package org.example.all_my_trip_project.domain.accommodation.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 화면에 그대로 그려지는 숙소 카드 한 장.
 *
 * <p>표시용 문자열을 서버가 만들어 내려준다. 화면이 계산하면 같은 규칙이 두 곳으로 갈린다.
 * 항공의 {@code FlightOfferResponse}가 {@code durationLabel}을 서버에서 만드는 것과 같은 이유다.
 *
 * @param nightsLabel      "2박" — 총액이 몇 박 기준인지 카드에 밝힌다
 * @param priceSourceLabel 요금 출처. 값이 없으면 이 문구가 가격 자리에 대신 들어간다
 * @param ribbons          카드 상단 강조. "AI 추천", "최저가"
 */
public record AccommodationOfferResponse(
        String offerId,
        String provider,
        String name,
        String type,
        String typeLabel,
        String areaLabel,
        String address,
        Double rating,
        Integer reviewCount,
        BigDecimal nightlyPrice,
        BigDecimal totalPrice,
        String currency,
        String nightsLabel,
        String priceSource,
        String priceSourceLabel,
        List<String> amenities,
        boolean freeCancellation,
        boolean breakfastIncluded,
        String deeplinkUrl,
        List<String> ribbons
) implements Serializable {

    public static AccommodationOfferResponse from(AccommodationOffer offer, int nights,
                                                  List<String> ribbons) {
        return new AccommodationOfferResponse(
                offer.offerId(),
                offer.provider(),
                offer.name(),
                offer.type().name(),
                offer.type().label(),
                offer.areaLabel(),
                offer.address(),
                offer.rating(),
                offer.reviewCount(),
                offer.nightlyPrice(),
                offer.totalPrice(),
                offer.currency(),
                nights + "박",
                offer.priceSource().name(),
                offer.priceSource().label(),
                offer.amenities(),
                offer.freeCancellation(),
                offer.breakfastIncluded(),
                offer.deeplinkUrl(),
                ribbons
        );
    }
}
