package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.dto.TripDraftSnapshotResponse;
import org.example.all_my_trip_project.domain.trip.service.TripDraftSnapshotService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.SessionUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trip-drafts")
public class TripDraftSnapshotController {

    private final TripDraftSnapshotService service;
    private final SessionUserResolver sessionUserResolver;

    public TripDraftSnapshotController(TripDraftSnapshotService service, SessionUserResolver sessionUserResolver) {
        this.service = service;
        this.sessionUserResolver = sessionUserResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripDraftSnapshotResponse>> create(
            @RequestBody Map<String, Object> draft,
            HttpServletRequest request
    ) {
        validate(draft);
        TripDraftSnapshotResponse response = service.create(sessionUserResolver.requiredUserId(request), draft);
        return ResponseEntity
                .created(URI.create("/api/v1/trip-drafts/" + response.draftId()))
                .body(ApiResponse.success("여행 초안이 서버에 저장되었습니다.", response));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<TripDraftSnapshotResponse> get(@PathVariable String draftId, HttpServletRequest request) {
        return ApiResponse.success("여행 초안을 불러왔습니다.", service.get(draftId, sessionUserResolver.requiredUserId(request)));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<TripDraftSnapshotResponse> update(
            @PathVariable String draftId,
            @RequestBody Map<String, Object> draft,
            HttpServletRequest request
    ) {
        validate(draft);
        return ApiResponse.success("여행 초안이 서버에 갱신되었습니다.", service.update(
                draftId, sessionUserResolver.requiredUserId(request), draft));
    }

    private void validate(Map<String, Object> draft) {
        if (draft == null || draft.isEmpty()) {
            throw new IllegalArgumentException("저장할 여행 초안이 필요합니다.");
        }
    }
}
