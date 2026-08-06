package org.example.all_my_trip_project.domain.ai.dto;

public record AiTripPlanSaveResult(
        Long tripId,
        int savedDayCount,
        int savedItineraryItemCount
) {
}
