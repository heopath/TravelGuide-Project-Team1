package org.example.all_my_trip_project.domain.trip.repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public interface TripDraftSnapshotRepository {

    StoredTripDraft create(Long userId, Map<String, Object> draft);

    Optional<StoredTripDraft> findById(String draftId, Long userId);

    Optional<StoredTripDraft> update(String draftId, Long userId, Map<String, Object> draft);

    record StoredTripDraft(
            String draftId,
            Long userId,
            Map<String, Object> draft,
            OffsetDateTime savedAt
    ) {
    }
}
