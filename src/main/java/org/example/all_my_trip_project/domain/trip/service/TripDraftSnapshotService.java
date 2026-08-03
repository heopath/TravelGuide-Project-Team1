package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dto.TripDraftSnapshotResponse;
import org.example.all_my_trip_project.domain.trip.repository.TripDraftSnapshotRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TripDraftSnapshotService {

    private final TripDraftSnapshotRepository repository;

    public TripDraftSnapshotService(TripDraftSnapshotRepository repository) {
        this.repository = repository;
    }

    public TripDraftSnapshotResponse create(Map<String, Object> draft) {
        return response(repository.create(draft));
    }

    public TripDraftSnapshotResponse get(String draftId) {
        return repository.findById(draftId)
                .map(this::response)
                .orElseThrow(() -> new DraftNotFoundException(draftId));
    }

    public TripDraftSnapshotResponse update(String draftId, Map<String, Object> draft) {
        return repository.update(draftId, draft)
                .map(this::response)
                .orElseThrow(() -> new DraftNotFoundException(draftId));
    }

    private TripDraftSnapshotResponse response(TripDraftSnapshotRepository.StoredTripDraft stored) {
        return new TripDraftSnapshotResponse(
                stored.draftId(),
                "SAVED",
                "/trips/recommendations",
                stored.savedAt(),
                stored.draft()
        );
    }

    public static class DraftNotFoundException extends RuntimeException {
        public DraftNotFoundException(String draftId) {
            super("여행 초안을 찾을 수 없습니다. draftId=" + draftId);
        }
    }
}
