package org.example.all_my_trip_project.domain.ai.dto;

public record AiGuideItemResponse(
        String time,
        String name,
        String reason,
        Long placeId,
        String placeCategory,
        String placeAddress,
        String placeUrl
) {
    public AiGuideItemResponse(String time, String name, String reason) {
        this(time, name, reason, null, null, null, null);
    }
}
