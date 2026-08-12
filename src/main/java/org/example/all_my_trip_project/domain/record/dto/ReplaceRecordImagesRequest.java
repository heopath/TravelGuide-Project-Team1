package org.example.all_my_trip_project.domain.record.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;

import java.util.List;

public record ReplaceRecordImagesRequest(
        @NotNull @Size(max = RecordPolicy.MAX_IMAGE_COUNT) List<@NotNull @Valid ImageItem> images
) {
    public record ImageItem(
            @NotBlank @Size(max = RecordPolicy.MAX_IMAGE_URL_LENGTH) String imageUrl,
            @Size(max = RecordPolicy.MAX_IMAGE_ALT_TEXT_LENGTH) String altText,
            boolean cover
    ) {
    }
}
