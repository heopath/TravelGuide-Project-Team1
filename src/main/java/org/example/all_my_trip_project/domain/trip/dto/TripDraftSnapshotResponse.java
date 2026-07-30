package org.example.all_my_trip_project.domain.trip.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record TripDraftSnapshotResponse(
        String draftId,
        String status,
        String nextUrl,
        OffsetDateTime savedAt,
        Map<String, Object> draft
) {
}
