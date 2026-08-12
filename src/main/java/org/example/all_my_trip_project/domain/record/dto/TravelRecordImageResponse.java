package org.example.all_my_trip_project.domain.record.dto;

public record TravelRecordImageResponse(
        Long travelRecordImageId,
        String imageUrl,
        String altText,
        int sortOrder,
        boolean cover
) {
}
