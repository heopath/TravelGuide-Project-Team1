package org.example.all_my_trip_project.domain.accommodation.service;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 추천순 정렬 점수.
 *
 * <p><b>항공의 일정 적합도를 그대로 옮길 수 없다.</b> 항공은 출발·도착 시각이
 * 일정과 겹치는지로 판단했지만(#133), 숙소는 시각이 아니라 그날 동선상의 위치가 기준이다.
 * 위치 기반 점수는 숙소 좌표와 일정 장소 좌표가 둘 다 있어야 하는데, 현재 Mock provider는
 * 좌표를 주지 않는다. 그래서 이번 단계에서는 가격과 평판만 본다.
 *
 * <p>동선 적합도가 붙기 전까지 추천순은 "싸고 평이 좋은 순"이다.
 * 최저가순과 완전히 같지는 않지만 크게 다르지도 않다. 화면에 그렇게 설명한다.
 */
@Component
public class AccommodationRecommendationScorer {

    /** 가격이 선택을 가장 크게 좌우한다. 다만 평점을 무시할 만큼은 아니다. */
    private static final double PRICE_WEIGHT = 0.6;
    private static final double RATING_WEIGHT = 0.4;

    private static final double MAX_RATING = 5.0;

    /**
     * 요금을 못 받은 숙소에 줄 가격 점수.
     *
     * <p>0을 주면 목록 맨 뒤로 밀려 사실상 사라지고, 1을 주면 최저가로 취급돼 맨 앞에 온다.
     * 둘 다 사실과 다르다. 중간값을 줘서 평점으로만 순위가 갈리게 둔다.
     */
    private static final double UNPRICED_PRICE_SCORE = 0.5;

    public List<AccommodationOffer> score(List<AccommodationOffer> offers) {
        if (offers.isEmpty()) {
            return offers;
        }

        BigDecimal min = offers.stream()
                .filter(AccommodationOffer::hasPrice)
                .map(AccommodationOffer::totalPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal max = offers.stream()
                .filter(AccommodationOffer::hasPrice)
                .map(AccommodationOffer::totalPrice)
                .max(BigDecimal::compareTo)
                .orElse(null);

        return offers.stream()
                .map(offer -> offer.withScore(
                        PRICE_WEIGHT * priceScore(offer, min, max)
                                + RATING_WEIGHT * ratingScore(offer)))
                .toList();
    }

    /** 최저가가 1, 최고가가 0. 전부 같은 가격이면 비교 의미가 없으므로 다 같은 점수를 준다. */
    private double priceScore(AccommodationOffer offer, BigDecimal min, BigDecimal max) {
        if (!offer.hasPrice() || min == null) {
            return UNPRICED_PRICE_SCORE;
        }
        if (min.compareTo(max) == 0) {
            return 1.0;
        }
        double range = max.subtract(min).doubleValue();
        return 1.0 - (offer.totalPrice().subtract(min).doubleValue() / range);
    }

    /** 평점이 없으면 0점이 아니라 중간이다. 신규 숙소를 나쁜 숙소로 취급하지 않는다. */
    private double ratingScore(AccommodationOffer offer) {
        if (offer.rating() == null) {
            return 0.5;
        }
        return Math.min(1.0, offer.rating() / MAX_RATING);
    }
}
