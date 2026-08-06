package org.example.all_my_trip_project.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiTripPlanSaveRequest(
        @Size(max = 150) String title,
        @NotNull @Valid AiTripPlanRequest conditions,
        @NotNull AiTripPlanResponse plan,
        @Valid List<AiTripPlanResolvedPlace> resolvedPlaces
) {
}
