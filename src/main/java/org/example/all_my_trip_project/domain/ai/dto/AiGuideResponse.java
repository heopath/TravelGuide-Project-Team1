package org.example.all_my_trip_project.domain.ai.dto;

import java.util.List;

public record AiGuideResponse(
        String answer,
        List<AiGuideDayResponse> days,
        List<ExternalLink> externalLinks,
        List<String> sources
) {
    public record ExternalLink(
            String type,
            String label,
            String url
    ) {
    }
}
