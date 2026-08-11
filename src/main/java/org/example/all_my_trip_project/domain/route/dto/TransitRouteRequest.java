package org.example.all_my_trip_project.domain.route.dto;

import jakarta.validation.constraints.NotNull;

public record TransitRouteRequest(
        @NotNull Double startX,
        @NotNull Double startY,
        @NotNull Double endX,
        @NotNull Double endY
) {}
