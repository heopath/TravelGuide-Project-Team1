package org.example.all_my_trip_project.domain.record.dto;

import org.example.all_my_trip_project.domain.record.type.RecordVisibility;

import java.time.OffsetDateTime;
import java.util.List;

public record TravelRecordResponse(
        Long travelRecordId,
        Long tripId,
        Long userId,
        String title,
        String content,
        Short rating,
        RecordVisibility visibility,
        List<TravelRecordImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
