package org.example.all_my_trip_project.domain.booking.dto;

import java.util.UUID;

public record BookingBatchResponse(
        UUID bookingBatchId,
        Long tripId,
        int flightCount,
        int accommodationCount,
        boolean linked
) {}
