package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dto.TripDraftSnapshotResponse;
import org.example.all_my_trip_project.domain.trip.repository.TripDraftSnapshotRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TripDraftSnapshotService {

    private final TripDraftSnapshotRepository repository;

    public TripDraftSnapshotService(TripDraftSnapshotRepository repository) {
        this.repository = repository;
    }

    public TripDraftSnapshotResponse create(Long userId, Map<String, Object> draft) {
        return response(repository.create(userId, draft));
    }

    public TripDraftSnapshotResponse get(String draftId, Long userId) {
        return repository.findById(draftId, userId)
                .map(this::response)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DRAFT_NOT_FOUND));
    }

    public TripDraftSnapshotResponse update(String draftId, Long userId, Map<String, Object> draft) {
        return repository.update(draftId, userId, draft)
                .map(this::response)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DRAFT_NOT_FOUND));
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

}
