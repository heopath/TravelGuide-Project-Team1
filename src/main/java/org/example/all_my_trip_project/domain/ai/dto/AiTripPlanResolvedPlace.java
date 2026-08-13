package org.example.all_my_trip_project.domain.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AiTripPlanResolvedPlace(
        @Min(1) @Max(30) int day,
        @Min(1) @Max(10) int number,
        @NotBlank @Size(max = 100) String externalPlaceId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String address,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Size(max = 50) String phone,
        @Size(max = 500) String websiteUrl,
        // 카카오 category_name의 전체 경로("서비스,산업 > 여행,관광 > 관광,명소 > 유원지")가 들어온다.
        // places.category(VARCHAR 30)에는 이 값을 매핑한 enum만 저장하므로 컬럼 길이와 무관하다.
        @Size(max = 255) String category,
        String description
) {
}
