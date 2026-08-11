package org.example.all_my_trip_project.domain.route.dto;

import java.util.List;

public record TransitRouteResponse(
        int totalDurationSeconds,
        int totalDistanceMeters,
        int totalWalkMeters,
        List<TransitSection> sections,
        List<RoutePoint> points
) {
    public record TransitSection(
            String mode,
            String fromName,
            String toName,
            String routeName,
            int durationSeconds,
            int distanceMeters
    ) {}

    public record RoutePoint(
            double longitude,
            double latitude
    ) {}
}
