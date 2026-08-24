package org.example.all_my_trip_project.domain.route.dto;

import java.util.List;

public record RouteOptimizationResponse(
        List<Long> itineraryItemIds,
        List<String> titles,
        int totalDurationSeconds,
        int totalDistanceMeters,
        boolean distancePriorityApplied,
        List<RouteSegment> segments,
        int originalDurationSeconds,
        int originalDistanceMeters,
        int optimizedDurationSeconds,
        int optimizedDistanceMeters,
        int savedDurationSeconds,
        boolean originalRouteAvailable,
        List<UnavailableRoute> unavailableRoutes
) {
    public record RouteSegment(
            Long fromItineraryItemId,
            String fromTitle,
            Long toItineraryItemId,
            String toTitle,
            int durationSeconds,
            int distanceMeters
    ) {}

    public record UnavailableRoute(
            Long fromItineraryItemId,
            String fromTitle,
            Long toItineraryItemId,
            String toTitle
    ) {}
}
