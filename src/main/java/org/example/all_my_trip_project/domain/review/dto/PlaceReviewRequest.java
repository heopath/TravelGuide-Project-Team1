package org.example.all_my_trip_project.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceReviewRequest(
        @NotNull @Min(1) @Max(5) Short rating,
        @NotBlank @Size(max = 1000) String content
) {
}
