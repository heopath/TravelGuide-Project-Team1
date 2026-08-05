package org.example.all_my_trip_project.domain.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AiTripPlanRequest(
        @NotBlank(message = "목적지를 입력해 주세요.")
        String destination,
        @NotNull(message = "여행 시작일을 입력해 주세요.")
        LocalDate startDate,
        @NotNull(message = "여행 종료일을 입력해 주세요.")
        LocalDate endDate,
        @Min(value = 1, message = "여행 인원은 1명 이상이어야 합니다.")
        @Max(value = 20, message = "여행 인원은 20명 이하여야 합니다.")
        int travelers,
        @NotBlank(message = "동행 유형을 선택해 주세요.")
        String companion,
        @NotBlank(message = "여행 테마를 선택해 주세요.")
        String theme,
        @NotBlank(message = "일정 속도를 선택해 주세요.")
        String pace,
        @NotBlank(message = "예산 정도를 선택해 주세요.")
        String budget
) {
}
