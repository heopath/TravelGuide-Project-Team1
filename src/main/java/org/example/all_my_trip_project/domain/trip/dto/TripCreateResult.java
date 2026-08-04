package org.example.all_my_trip_project.domain.trip.dto;

import java.util.List;

public record TripCreateResult(
        TripDTO trip,
        List<TripDayDTO> days
) {
}
