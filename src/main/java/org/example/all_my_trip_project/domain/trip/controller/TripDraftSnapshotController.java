package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.dto.TripDraftSnapshotResponse;
import org.example.all_my_trip_project.domain.trip.service.TripDraftSnapshotService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/trip-drafts")
public class TripDraftSnapshotController {

    private final TripDraftSnapshotService service;

    public TripDraftSnapshotController(TripDraftSnapshotService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripDraftSnapshotResponse>> create(
            @RequestBody Map<String, Object> draft
    ) {
        validate(draft);
        TripDraftSnapshotResponse response = service.create(draft);
        return ResponseEntity
                .created(URI.create("/api/trip-drafts/" + response.draftId()))
                .body(ApiResponse.success("여행 초안이 서버에 저장되었습니다.", response));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<TripDraftSnapshotResponse> get(@PathVariable String draftId) {
        return ApiResponse.success("여행 초안을 불러왔습니다.", service.get(draftId));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<TripDraftSnapshotResponse> update(
            @PathVariable String draftId,
            @RequestBody Map<String, Object> draft
    ) {
        validate(draft);
        return ApiResponse.success("여행 초안이 서버에 갱신되었습니다.", service.update(draftId, draft));
    }

    @ExceptionHandler(TripDraftSnapshotService.DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            TripDraftSnapshotService.DraftNotFoundException error
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, "TRIP_DRAFT_NOT_FOUND", error.getMessage(), null));
    }

    private void validate(Map<String, Object> draft) {
        if (draft == null || draft.get("basic") == null || draft.get("style") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기본 정보와 여행 스타일이 필요합니다.");
        }
    }
}
