package org.example.all_my_trip_project.domain.route.service;

import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Component
@Profile("!ui")
final class RouteOrderOptimizer {

    Result optimize(
            List<ItineraryItemDTO> items,
            BiFunction<ItineraryItemDTO, ItineraryItemDTO, Metrics> metricsProvider,
            boolean timeFirst) {
        if (items.size() < 2) return new Result(List.copyOf(items), 0, 0, false);

        SearchState state = new SearchState(timeFirst);
        List<ItineraryItemDTO> order = new ArrayList<>();
        order.add(items.get(0));
        search(
                order,
                new ArrayList<>(items.subList(1, items.size())),
                0,
                0,
                metricsProvider,
                state);
        if (state.bestOrder == null) {
            throw new IllegalArgumentException("모든 장소를 연결할 수 있는 이동 경로가 없습니다.");
        }
        return new Result(
                List.copyOf(state.bestOrder),
                state.bestDurationSeconds,
                state.bestDistanceMeters,
                state.distancePriorityApplied);
    }

    private void search(
            List<ItineraryItemDTO> order,
            List<ItineraryItemDTO> remaining,
            int durationSeconds,
            int distanceMeters,
            BiFunction<ItineraryItemDTO, ItineraryItemDTO, Metrics> metricsProvider,
            SearchState state) {
        if (remaining.isEmpty()) {
            state.consider(order, durationSeconds, distanceMeters);
            return;
        }
        ItineraryItemDTO from = order.get(order.size() - 1);
        for (int index = 0; index < remaining.size(); index++) {
            ItineraryItemDTO to = remaining.get(index);
            Metrics metrics = metricsProvider.apply(from, to);
            if (metrics == null) continue;

            List<ItineraryItemDTO> nextRemaining = new ArrayList<>(remaining);
            nextRemaining.remove(index);
            order.add(to);
            search(
                    order,
                    nextRemaining,
                    durationSeconds + metrics.durationSeconds(),
                    distanceMeters + metrics.distanceMeters(),
                    metricsProvider,
                    state);
            order.remove(order.size() - 1);
        }
    }

    record Metrics(int durationSeconds, int distanceMeters) {
    }

    record Result(
            List<ItineraryItemDTO> orderedItems,
            int durationSeconds,
            int distanceMeters,
            boolean distancePriorityApplied) {
    }

    private static final class SearchState {
        private final boolean timeFirst;
        private List<ItineraryItemDTO> bestOrder;
        private int bestDurationSeconds = Integer.MAX_VALUE;
        private int bestDistanceMeters = Integer.MAX_VALUE;
        private boolean distancePriorityApplied;

        private SearchState(boolean timeFirst) {
            this.timeFirst = timeFirst;
        }

        private void consider(List<ItineraryItemDTO> order, int durationSeconds, int distanceMeters) {
            int primary = timeFirst ? durationSeconds : distanceMeters;
            int bestPrimary = timeFirst ? bestDurationSeconds : bestDistanceMeters;
            int secondary = timeFirst ? distanceMeters : durationSeconds;
            int bestSecondary = timeFirst ? bestDistanceMeters : bestDurationSeconds;
            if (primary < bestPrimary) {
                bestOrder = List.copyOf(order);
                bestDurationSeconds = durationSeconds;
                bestDistanceMeters = distanceMeters;
                distancePriorityApplied = false;
                return;
            }
            if (primary != bestPrimary) return;
            if (timeFirst && secondary != bestSecondary) distancePriorityApplied = true;
            if (secondary < bestSecondary) {
                bestOrder = List.copyOf(order);
                bestDurationSeconds = durationSeconds;
                bestDistanceMeters = distanceMeters;
            }
        }
    }
}
