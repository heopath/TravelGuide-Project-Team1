package org.example.all_my_trip_project.domain.accommodation.dto;

import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 숙소 하나. provider가 만들고 composite가 요금을 덮어쓴다.
 *
 * @param offerId       provider 접두어를 붙인 식별자. "mock:ocean-stay-0910"
 * @param nightlyPrice  1박 요금. 요금 미제공이면 null
 * @param totalPrice    박 수 × 객실 수를 곱한 총액. 요금 미제공이면 null
 * @param imageUrl      목록 카드 대표 이미지. 제공되지 않으면 null
 * @param latitude      숙소 위도. 동선 추천에 사용하며 제공되지 않으면 null
 * @param longitude     숙소 경도. 동선 추천에 사용하며 제공되지 않으면 null
 * @param score         추천순 정렬 기준. composite가 채운다
 *
 * <p><b>1박 요금과 총액을 둘 다 들고 다닌다.</b> 화면은 카드에 1박 요금을 보여주고
 * 우측 예약 현황 패널에는 총액을 더한다. 화면에서 곱하면 반올림 규칙이 두 곳으로 갈린다.
 */
public record AccommodationOffer(
        String offerId,
        String provider,
        String name,
        AccommodationType type,
        String areaLabel,
        String address,
        Double rating,
        Integer reviewCount,
        BigDecimal nightlyPrice,
        BigDecimal totalPrice,
        String currency,
        AccommodationPriceSource priceSource,
        List<String> amenities,
        boolean freeCancellation,
        boolean breakfastIncluded,
        String imageUrl,
        Double latitude,
        Double longitude,
        String deeplinkUrl,
        double score
) {

    /** 요금을 받지 못한 숙소는 정렬·합계에서 빠진다. 0원으로 다루면 최저가가 된다. */
    public boolean hasPrice() {
        return totalPrice != null && totalPrice.signum() > 0;
    }

    public AccommodationOffer withScore(double newScore) {
        return new AccommodationOffer(offerId, provider, name, type, areaLabel, address,
                rating, reviewCount, nightlyPrice, totalPrice, currency, priceSource,
                amenities, freeCancellation, breakfastIncluded, imageUrl, latitude, longitude,
                deeplinkUrl, newScore);
    }

    public AccommodationOffer withPrice(BigDecimal newNightlyPrice, BigDecimal newTotalPrice,
                                        AccommodationPriceSource newSource) {
        return new AccommodationOffer(offerId, provider, name, type, areaLabel, address,
                rating, reviewCount, newNightlyPrice, newTotalPrice, currency, newSource,
                amenities, freeCancellation, breakfastIncluded, imageUrl, latitude, longitude,
                deeplinkUrl, score);
    }

    /**
     * 외부 요금 공급자가 확인한 숙박 전체 요금과 취소·조식 조건을 붙인다.
     * 통화는 검색 요청과 다르게 반환될 수 있으므로 공급자 응답 값을 사용한다.
     */
    public AccommodationOffer withRate(BigDecimal newTotalPrice, String newCurrency,
                                       AccommodationPriceSource newSource, int nights, int rooms,
                                       boolean newFreeCancellation, boolean newBreakfastIncluded) {
        long divisor = Math.max(1L, (long) nights * Math.max(1, rooms));
        BigDecimal newNightlyPrice = newTotalPrice.divide(
                BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
        return new AccommodationOffer(offerId, provider, name, type, areaLabel, address,
                rating, reviewCount, newNightlyPrice, newTotalPrice, newCurrency, newSource,
                amenities, newFreeCancellation, newBreakfastIncluded, imageUrl, latitude, longitude,
                deeplinkUrl, score);
    }
}
