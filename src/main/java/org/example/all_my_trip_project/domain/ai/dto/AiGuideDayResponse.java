package org.example.all_my_trip_project.domain.ai.dto;

import java.util.List;

public record AiGuideDayResponse(
        int day,
        String title,
        List<AiGuideItemResponse> items
) {
}
