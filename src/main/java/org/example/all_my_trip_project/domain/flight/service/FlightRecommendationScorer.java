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

    private final FlightScheduleAnalyzer scheduleAnalyzer;

    public List<FlightOffer> rank(List<FlightOffer> offers, FlightSearchQuery query) {
        if (offers == null || offers.isEmpty()) {
            return List.of();
        }

        BigDecimal min = offers.stream().map(FlightOffer::totalPrice).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = offers.stream().map(FlightOffer::totalPrice).max(BigDecimal::compareTo).orElseThrow();

        record Scored(FlightOffer offer, double score) {}

        List<Scored> scored = new ArrayList<>(offers.stream()
                .map(offer -> new Scored(
                        offer.withBadges(scheduleAnalyzer.badges(offer, query)),
                        PRICE_WEIGHT * priceScore(offer.totalPrice(), min, max)
                                + SCHEDULE_WEIGHT * scheduleAnalyzer.scheduleScore(offer, query)))
                .toList());

        // 동점이면 싼 편 먼저, 그래도 같으면 일찍 출발하는 편 먼저 — 매 호출 같은 순서가 나와야 한다.
        scored.sort(Comparator
                .comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.offer().totalPrice())
                .thenComparing(s -> s.offer().departureAt()));

        return attachRibbons(scored.stream().map(Scored::offer).toList(), min);
    }

    private double priceScore(BigDecimal price, BigDecimal min, BigDecimal max) {
        // 후보 운임이 전부 같으면 가격으로 우열을 가릴 수 없으므로 감점하지 않는다.
        if (max.compareTo(min) == 0) {
            return 1.0;
        }
        return max.subtract(price).doubleValue() / max.subtract(min).doubleValue();
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
            if (!cheapestAssigned && offer.totalPrice().compareTo(minPrice) == 0) {
                badges.add(Badge.LOWEST_PRICE);
                cheapestAssigned = true;
            }
            badges.addAll(offer.badges());

            result.add(offer.withBadges(badges));
        }
        return result;
    }
}
