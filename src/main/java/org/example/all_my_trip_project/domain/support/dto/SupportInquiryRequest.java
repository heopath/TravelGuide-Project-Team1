package org.example.all_my_trip_project.domain.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupportInquiryRequest(
        @NotBlank
        @Pattern(regexp = "TRIP_PLAN|AI_PLAN|PLACE_FAVORITE|BOOKING|ACCOUNT|ERROR|OTHER")
        String category,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 3000) String content
) {
}
