package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record KakaoPlaceCreateRequest(
        @NotBlank @Size(max = 100) String externalPlaceId,
        @NotBlank
        @Pattern(regexp = "ATTRACTION|RESTAURANT|CAFE|ACCOMMODATION|FESTIVAL|ACTIVITY|TRANSPORT")
        String category,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String region,
        @Size(max = 100) String city,
        @Size(max = 255) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 50) String phone,
        @Size(max = 500) String websiteUrl
) {
}
