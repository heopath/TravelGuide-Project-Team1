package org.example.all_my_trip_project.domain.place.dto;

public record PlaceCreationResult(
        PlaceDTO place,
        boolean created
) {
}
