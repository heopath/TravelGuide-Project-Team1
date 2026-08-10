package org.example.all_my_trip_project.domain.record.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.CreateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordResponse;
import org.example.all_my_trip_project.domain.record.dto.UpdateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.service.TravelRecordService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/travel-records")
@RequiredArgsConstructor
public class TravelRecordController {

    private final TravelRecordService travelRecordService;

    @PostMapping
    public ResponseEntity<ApiResponse<TravelRecordResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTravelRecordRequest request) {
        TravelRecordResponse response = travelRecordService.create(requireUserId(principal), request);
        return ResponseEntity.created(URI.create("/api/v1/travel-records/" + response.travelRecordId()))
                .body(ApiResponse.success("여행 기록이 작성되었습니다.", response));
    }

    @GetMapping("/{travelRecordId}")
    public ApiResponse<TravelRecordResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long travelRecordId) {
        Long viewerUserId = principal == null ? null : principal.userId();
        return ApiResponse.success(travelRecordService.get(viewerUserId, travelRecordId));
    }

    @GetMapping("/me")
    public ApiResponse<List<TravelRecordResponse>> getMyRecords(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(travelRecordService.getMyRecords(requireUserId(principal)));
    }

    @PutMapping("/{travelRecordId}")
    public ApiResponse<TravelRecordResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long travelRecordId,
            @Valid @RequestBody UpdateTravelRecordRequest request) {
        TravelRecordResponse response =
                travelRecordService.update(requireUserId(principal), travelRecordId, request);
        return ApiResponse.success("여행 기록이 수정되었습니다.", response);
    }

    @PutMapping("/{travelRecordId}/images")
    public ApiResponse<TravelRecordResponse> replaceImages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long travelRecordId,
            @Valid @RequestBody ReplaceRecordImagesRequest request) {
        TravelRecordResponse response =
                travelRecordService.replaceImages(requireUserId(principal), travelRecordId, request);
        return ApiResponse.success("여행 기록 이미지가 수정되었습니다.", response);
    }

    @DeleteMapping("/{travelRecordId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long travelRecordId) {
        travelRecordService.delete(requireUserId(principal), travelRecordId);
        return ResponseEntity.ok(ApiResponse.success("여행 기록이 삭제되었습니다.", null));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
