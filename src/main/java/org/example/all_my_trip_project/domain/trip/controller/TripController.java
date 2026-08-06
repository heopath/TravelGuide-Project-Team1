package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @PostMapping
    public ResponseEntity<ApiResponse<TripCreateResult>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TripCreateRequest request) {
        Long userId = requireUserId(principal);
        TripCreateResult result = tripService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.tripId()))
                .body(ApiResponse.success("여행과 날짜별 일정이 생성되었습니다.", result));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDTO> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long tripId) {
        return ApiResponse.success(tripService.get(requireUserId(principal), tripId));
    }

    @GetMapping
    public ApiResponse<List<TripDTO>> getByUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(tripService.getByUser(requireUserId(principal)));
    }

    @PutMapping("/{tripId}")
    public ApiResponse<TripDTO> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long tripId, @RequestBody TripDTO trip) {
        Long userId = requireUserId(principal);
        trip.setTripId(tripId);
        trip.setUserId(userId);
        tripService.update(userId, trip);
        return ApiResponse.success("여행 정보가 수정되었습니다.", tripService.get(userId, tripId));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        tripService.delete(requireUserId(principal), tripId);
        return ResponseEntity.ok(ApiResponse.success("여행이 삭제되었습니다.", null));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
