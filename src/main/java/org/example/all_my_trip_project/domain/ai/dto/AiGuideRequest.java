package org.example.all_my_trip_project.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AiGuideRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 500, message = "질문은 500자 이하로 입력해 주세요.")
        String question,
        @NotNull(message = "tripId is required")
        @Positive(message = "tripId must be a positive number")
        Long tripId,
        @Positive(message = "selectedDayNumber must be a positive number")
        Integer selectedDayNumber,
        @Positive(message = "referencePlaceId must be a positive number")
        Long referencePlaceId
) {

    public AiGuideRequest(String question, Long tripId) {
        this(question, tripId, null, null);
    }

    public AiGuideRequest(String question, Long tripId, Integer selectedDayNumber) {
        this(question, tripId, selectedDayNumber, null);
    }

    public AiGuideRequest(String question, Long tripId, Long referencePlaceId) {
        this(question, tripId, null, referencePlaceId);
    }
}
