package org.example.all_my_trip_project.domain.flight.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.type.Badge;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 배지 계산 + 추천순 정렬.
 *
 * <pre>
 * score = 0.6 × priceScore + 0.4 × scheduleScore
 * </pre>
 *
 * <p>가격이 전부 공시운임이면 항공사 간 차이가 작아 priceScore가 잘 벌어지지 않는다.
 * 그때는 사실상 일정 적합도가 순위를 결정하는데, 그게 이 제품의 원래 의도에 더 가깝다.
 *
 * <p>프론트의 `추천순`은 이 순서를 그대로 쓴다. 클라이언트에서 다시 계산하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FlightRecommendationScorer {

    private static final double PRICE_WEIGHT = 0.6;
    private static final double SCHEDULE_WEIGHT = 0.4;

    /**
     * 운임을 모르는 항공편의 가격 점수.
     *
     * <p>0을 주면 목록 맨 아래로 밀린다. 싸서 추천되는 일도, 비싸다고 단정하는 일도 없어야 하는데
     * 둘 중 하나를 골라야 한다면 "추천하지 않는" 쪽이 안전하다.
     * 값을 모르는 항공편을 추천 1위로 올려놓고 사용자를 예약 사이트로 보낼 수는 없다.
     */
    private static final double UNKNOWN_PRICE_SCORE = 0.0;

    private final FlightScheduleAnalyzer scheduleAnalyzer;

    public List<FlightOffer> rank(List<FlightOffer> offers, FlightSearchQuery query) {
        if (offers == null || offers.isEmpty()) {
            return List.of();
        }

        List<BigDecimal> prices = offers.stream()
                .filter(FlightOffer::hasPrice)
                .map(FlightOffer::totalPrice)
                .toList();
        Optional<BigDecimal> min = prices.stream().min(BigDecimal::compareTo);
        Optional<BigDecimal> max = prices.stream().max(BigDecimal::compareTo);

        record Scored(FlightOffer offer, double score) {}

        List<Scored> scored = new ArrayList<>(offers.stream()
                .map(offer -> new Scored(
                        offer.withBadges(scheduleAnalyzer.badges(offer, query)),
                        PRICE_WEIGHT * priceScore(offer, min, max)
                                + SCHEDULE_WEIGHT * scheduleAnalyzer.scheduleScore(offer, query)))
                .toList());

        // 동점이면 싼 편 먼저, 그래도 같으면 일찍 출발하는 편 먼저 — 매 호출 같은 순서가 나와야 한다.
        // 운임을 모르는 편은 가격 비교 대상이 아니므로 뒤로 보낸다.
        scored.sort(Comparator
                .comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.offer().hasPrice() ? 0 : 1)
                .thenComparing(s -> s.offer().hasPrice() ? s.offer().totalPrice() : BigDecimal.ZERO)
                .thenComparing(s -> s.offer().departureAt()));

        return attachRibbons(scored.stream().map(Scored::offer).toList(), min.orElse(null));
    }

    private double priceScore(FlightOffer offer, Optional<BigDecimal> min, Optional<BigDecimal> max) {
        if (!offer.hasPrice() || min.isEmpty()) {
            return UNKNOWN_PRICE_SCORE;
        }
        // 후보 운임이 전부 같으면 가격으로 우열을 가릴 수 없으므로 감점하지 않는다.
        if (max.get().compareTo(min.get()) == 0) {
            return 1.0;
        }
        return max.get().subtract(offer.totalPrice()).doubleValue()
                / max.get().subtract(min.get()).doubleValue();
    }

    /** 1위에 AI 추천, 최저가에 최저가. 같은 편이 둘 다 받을 수도 있다. */
    private List<FlightOffer> attachRibbons(List<FlightOffer> ranked, BigDecimal minPrice) {
        boolean cheapestAssigned = false;
        List<FlightOffer> result = new ArrayList<>(ranked.size());

        for (int i = 0; i < ranked.size(); i++) {
            FlightOffer offer = ranked.get(i);
            List<Badge> badges = new ArrayList<>();

            if (i == 0) {
                badges.add(Badge.AI_PICK);
            }
            // 운임을 모르는 편에는 최저가 배지를 붙일 수 없다.
            if (!cheapestAssigned && minPrice != null && offer.hasPrice()
                    && offer.totalPrice().compareTo(minPrice) == 0) {
                badges.add(Badge.LOWEST_PRICE);
                cheapestAssigned = true;
            }
            badges.addAll(offer.badges());

            result.add(offer.withBadges(badges));
        }
        return result;
    }
}
