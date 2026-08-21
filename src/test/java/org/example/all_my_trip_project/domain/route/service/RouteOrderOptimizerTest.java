package org.example.all_my_trip_project.domain.route.service;

import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOrderOptimizerTest {

    private final RouteOrderOptimizer optimizer = new RouteOrderOptimizer();

    @Test
    void findsGlobalMinimumThatNearestNeighborWouldMiss() {
        ItineraryItemDTO a = item(1L, "A");
        ItineraryItemDTO b = item(2L, "B");
        ItineraryItemDTO c = item(3L, "C");
        ItineraryItemDTO d = item(4L, "D");
        Map<String, RouteOrderOptimizer.Metrics> routes = routesWithDefault(List.of(a, b, c, d), 50, 5_000);
        route(routes, a, b, 1, 100);
        route(routes, a, c, 2, 200);
        route(routes, b, c, 100, 10_000);
        route(routes, b, d, 2, 200);
        route(routes, c, b, 2, 200);
        route(routes, c, d, 1, 100);

        RouteOrderOptimizer.Result result = optimizer.optimize(
                List.of(a, b, c, d),
                (from, to) -> routes.get(key(from, to)),
                true);

        assertThat(result.orderedItems())
                .extracting(ItineraryItemDTO::getItineraryItemId)
                .containsExactly(1L, 3L, 2L, 4L);
        assertThat(result.durationSeconds()).isEqualTo(6);
        assertThat(result.distanceMeters()).isEqualTo(600);
    }

    @Test
    void usesDistanceAsSecondPriorityWhenTotalTimesAreEqual() {
        ItineraryItemDTO a = item(1L, "A");
        ItineraryItemDTO b = item(2L, "B");
        ItineraryItemDTO c = item(3L, "C");
        Map<String, RouteOrderOptimizer.Metrics> routes = new HashMap<>();
        route(routes, a, b, 10, 1_000);
        route(routes, b, c, 10, 1_000);
        route(routes, a, c, 10, 500);
        route(routes, c, b, 10, 500);

        RouteOrderOptimizer.Result result = optimizer.optimize(
                List.of(a, b, c),
                (from, to) -> routes.get(key(from, to)),
                true);

        assertThat(result.orderedItems())
                .extracting(ItineraryItemDTO::getItineraryItemId)
                .containsExactly(1L, 3L, 2L);
        assertThat(result.durationSeconds()).isEqualTo(20);
        assertThat(result.distanceMeters()).isEqualTo(1_000);
        assertThat(result.distancePriorityApplied()).isTrue();
    }

    @Test
    void prioritizesRouteDistanceThenTravelDuration() {
        ItineraryItemDTO a = item(1L, "A");
        ItineraryItemDTO b = item(2L, "B");
        ItineraryItemDTO c = item(3L, "C");
        Map<String, RouteOrderOptimizer.Metrics> routes = routesWithDefault(List.of(a, b, c), 500, 10_000);
        route(routes, a, b, 90, 100);
        route(routes, b, c, 90, 100);
        route(routes, a, c, 150, 50);
        route(routes, c, b, 10, 150);

        RouteOrderOptimizer.Result result = optimizer.optimize(
                List.of(a, b, c),
                (from, to) -> routes.get(key(from, to)),
                false);

        assertThat(result.orderedItems())
                .extracting(ItineraryItemDTO::getItineraryItemId)
                .containsExactly(1L, 3L, 2L);
        assertThat(result.distanceMeters()).isEqualTo(200);
        assertThat(result.durationSeconds()).isEqualTo(160);
    }

    @Test
    void keepsFirstPlaceAndSkipsUnavailableEdges() {
        ItineraryItemDTO a = item(1L, "A");
        ItineraryItemDTO b = item(2L, "B");
        ItineraryItemDTO c = item(3L, "C");
        Map<String, RouteOrderOptimizer.Metrics> routes = new HashMap<>();
        route(routes, a, c, 5, 500);
        route(routes, c, b, 5, 500);

        RouteOrderOptimizer.Result result = optimizer.optimize(
                List.of(a, b, c),
                (from, to) -> routes.get(key(from, to)),
                true);

        assertThat(result.orderedItems())
                .extracting(ItineraryItemDTO::getItineraryItemId)
                .containsExactly(1L, 3L, 2L);
    }

    private Map<String, RouteOrderOptimizer.Metrics> routesWithDefault(
            List<ItineraryItemDTO> items,
            int durationSeconds,
            int distanceMeters) {
        Map<String, RouteOrderOptimizer.Metrics> routes = new HashMap<>();
        for (ItineraryItemDTO from : items) {
            for (ItineraryItemDTO to : items) {
                if (from != to) route(routes, from, to, durationSeconds, distanceMeters);
            }
        }
        return routes;
    }

    private void route(
            Map<String, RouteOrderOptimizer.Metrics> routes,
            ItineraryItemDTO from,
            ItineraryItemDTO to,
            int durationSeconds,
            int distanceMeters) {
        routes.put(key(from, to), new RouteOrderOptimizer.Metrics(durationSeconds, distanceMeters));
    }

    private String key(ItineraryItemDTO from, ItineraryItemDTO to) {
        return from.getItineraryItemId() + ":" + to.getItineraryItemId();
    }

    private ItineraryItemDTO item(Long id, String title) {
        return ItineraryItemDTO.builder()
                .itineraryItemId(id)
                .placeId(id + 100)
                .title(title)
                .build();
    }
}
