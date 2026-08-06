package org.example.all_my_trip_project.domain.ai.dto;

public record AiTripPlanPlaceResponse(
        int number,
        String category,
        String name,
        String description,
        int mapX,
        int mapY
) {
}
