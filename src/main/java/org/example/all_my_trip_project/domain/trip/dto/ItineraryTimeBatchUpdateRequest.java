package org.example.all_my_trip_project.domain.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record ItineraryTimeBatchUpdateRequest(
        @NotEmpty List<@Valid ItemTime> items
) {
    public record ItemTime(
            @NotNull Long itineraryItemId,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime
    ) {
    }
}
