package org.example.all_my_trip_project.domain.flight.service;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.type.Badge;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 항공편이 여행 일정과 얼마나 부딪히는지 계산한다.
 *
 * <p>일정 충돌 배지와 추천 스코어의 일정 적합도가 같은 판정에서 나와야 한다.
 * 두 곳에서 따로 계산하면 "첫 일정 늦음" 배지가 붙었는데 추천 1위인 상황이 생긴다.
 */
@Component
public class FlightScheduleAnalyzer {

    private static final double PENALTY_LATE_FOR_FIRST_PLAN = 0.5;
    private static final double PENALTY_MISSES_LAST_PLAN = 0.5;
    private static final double PENALTY_EARLY_DEPARTURE = 0.15;

    private static final LocalTime EARLY_DEPARTURE_BEFORE = LocalTime.of(6, 0);

    /** 일정 정보가 없으면 페널티가 0이라 모든 후보가 1.0이 되고, 순위는 가격만으로 갈린다. */
    public double scheduleScore(FlightOffer offer, FlightSearchQuery query) {
        double penalty = 0.0;
        if (isLateForFirstPlan(offer, query)) {
            penalty += PENALTY_LATE_FOR_FIRST_PLAN;
        }
        if (missesLastPlan(offer, query)) {
            penalty += PENALTY_MISSES_LAST_PLAN;
        }
        if (isEarlyDeparture(offer)) {
            penalty += PENALTY_EARLY_DEPARTURE;
        }
        return Math.clamp(1.0 - penalty, 0.0, 1.0);
    }

    public List<Badge> badges(FlightOffer offer, FlightSearchQuery query) {
        List<Badge> badges = new ArrayList<>(2);
        if (isLateForFirstPlan(offer, query)) {
            badges.add(Badge.LATE_FOR_FIRST_PLAN);
        }
        if (missesLastPlan(offer, query)) {
            badges.add(Badge.MISSES_LAST_PLAN);
        }
        if (isEarlyDeparture(offer)) {
            badges.add(Badge.EARLY_DEPARTURE);
        }
        return badges;
    }

    /** 가는 편이 1일차 첫 활동보다 늦게 도착하면 그 활동을 못 한다. */
    private boolean isLateForFirstPlan(FlightOffer offer, FlightSearchQuery query) {
        return query.firstPlanStartAt() != null
                && offer.arrivalAt().isAfter(query.firstPlanStartAt());
    }

    /** 오는 편이 마지막 활동이 끝나기 전에 출발하면 그 활동을 못 한다. */
    private boolean missesLastPlan(FlightOffer offer, FlightSearchQuery query) {
        return query.lastPlanEndAt() != null
                && offer.departureAt().isBefore(query.lastPlanEndAt());
    }

    private boolean isEarlyDeparture(FlightOffer offer) {
        return offer.departureAt().toLocalTime().isBefore(EARLY_DEPARTURE_BEFORE);
    }
}
