package org.example.all_my_trip_project.domain.trip.dto;

public record TripCreateResult(
        Long tripId,
        int createdDayCount
) {
}
