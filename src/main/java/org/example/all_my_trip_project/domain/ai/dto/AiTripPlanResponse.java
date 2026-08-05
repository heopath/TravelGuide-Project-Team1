package org.example.all_my_trip_project.domain.ai.dto;

import java.util.List;

public record AiTripPlanResponse(
        String title,
        String summary,
        List<AiTripPlanPlaceResponse> recommendedPlaces,
        List<AiTripPlanDayResponse> days,
        String generatedBy
) {
}
