package org.example.all_my_trip_project.domain.booking.dto;

import java.time.Instant;

public record BookingQueueStatusResponse(
        String token,
        BookingQueueState status,
        Long slotId,
        Long tripId,
        int position,
        int ahead,
        long estimatedWaitSeconds,
        int progressPercent,
        Instant expiresAt
) {
}
