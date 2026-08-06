package org.example.all_my_trip_project.domain.ai.dto;

import java.util.List;

public record AiTripPlanDayResponse(
        int day,
        String title,
        List<AiTripPlanItemResponse> items,
        List<AiTripPlanPlaceResponse> places
) {
}
