package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminPlaceRequest(
        @NotBlank
        @Pattern(regexp = "ATTRACTION|RESTAURANT|CAFE|ACCOMMODATION|FESTIVAL|ACTIVITY|TRANSPORT")
        String category,
        @NotBlank @Size(max = 150) String name,
        @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @Size(max = 100) String region,
        @Size(max = 100) String city,
        @Size(max = 255) String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 5000) String description,
        @Size(max = 50) String phone,
        @Size(max = 500) String websiteUrl,
        @Size(max = 1000) String primaryImageUrl,
        Boolean active,
        // 추천장소 화면 노출 여부. 생략하면 관리자 등록이므로 노출하는 것으로 본다.
        Boolean recommended
) {}
