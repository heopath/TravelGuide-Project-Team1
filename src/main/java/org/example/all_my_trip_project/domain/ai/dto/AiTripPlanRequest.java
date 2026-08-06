package org.example.all_my_trip_project.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.math.BigDecimal;

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
        @NotBlank(message = "여행 목적을 선택해 주세요.")
        String purpose,
        @NotBlank(message = "일정 속도를 선택해 주세요.")
        String pace,
        @JsonProperty("transport_preference")
        @NotBlank(message = "이동 선호를 선택해 주세요.")
        String transportPreference,
        @JsonProperty("food_preference")
        @NotBlank(message = "음식 선호를 선택해 주세요.")
        String foodPreference,
        @JsonProperty("accommodation_style")
        @NotBlank(message = "숙박 형태를 선택해 주세요.")
        String accommodationStyle,
        @JsonProperty("budget_amount")
        BigDecimal budgetAmount
) {
}
