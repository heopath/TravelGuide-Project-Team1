package org.example.all_my_trip_project.domain.user.dto;

import java.util.List;

public record UserPreferenceResponse(
        List<PreferenceItem> preferences
) {
    public UserPreferenceResponse {
        preferences = List.copyOf(preferences);
    }

    public record PreferenceItem(
            Short travelStyleId,
            String code,
            String name,
            Short preferenceScore,
            String source
    ) {
    }
}
