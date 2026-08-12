package org.example.all_my_trip_project.domain.record.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;

public record UpdateTravelRecordRequest(
        @NotBlank @Size(max = RecordPolicy.MAX_TITLE_LENGTH) String title,
        @NotBlank String content,
        @Min(RecordPolicy.MIN_RATING) @Max(RecordPolicy.MAX_RATING) Short rating,
        @NotNull RecordVisibility visibility
) {
}
