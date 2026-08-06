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
        Long tripId
) {
}
