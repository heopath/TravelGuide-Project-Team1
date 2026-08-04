package org.example.all_my_trip_project.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePreferencesRequest(
        @NotNull(message = "선호 목록은 필수입니다.")
        @Size(max = 20, message = "선호 스타일은 최대 20개까지 저장할 수 있습니다.")
        List<@NotNull(message = "선호 항목은 null일 수 없습니다.") @Valid PreferenceItem> preferences
) {
    public record PreferenceItem(
            @NotNull(message = "여행 스타일 ID는 필수입니다.")
            @Positive(message = "여행 스타일 ID는 1 이상이어야 합니다.")
            Short travelStyleId,

            @NotNull(message = "선호 점수는 필수입니다.")
            @Min(value = 0, message = "선호 점수는 0 이상이어야 합니다.")
            @Max(value = 100, message = "선호 점수는 100 이하여야 합니다.")
            Short preferenceScore
    ) {
    }
}
