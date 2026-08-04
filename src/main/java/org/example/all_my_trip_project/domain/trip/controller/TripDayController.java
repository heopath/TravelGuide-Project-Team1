package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.service.TripDayService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/days")
@RequiredArgsConstructor
public class TripDayController {
    private final TripDayService tripDayService;

    @PostMapping
    public ResponseEntity<ApiResponse<TripDayDTO>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @RequestBody TripDayDTO tripDay) {
        tripDay.setTripId(tripId);
        Long id = tripDayService.create(requireUserId(principal), tripDay);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + tripId + "/days/" + id))
                .body(ApiResponse.success("여행 일자가 생성되었습니다.", tripDay));
    }

    @GetMapping
    public ApiResponse<List<TripDayDTO>> getByTrip(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(tripDayService.getByTrip(requireUserId(principal), tripId));
    }

    @PutMapping("/{tripDayId}")
    public ApiResponse<TripDayDTO> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId, @PathVariable Long tripDayId,
            @RequestBody TripDayDTO tripDay) {
        tripDay.setTripId(tripId);
        tripDay.setTripDayId(tripDayId);
        tripDayService.update(requireUserId(principal), tripDay);
        return ApiResponse.success("여행 일자가 수정되었습니다.", tripDay);
    }

    @DeleteMapping("/{tripDayId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId, @PathVariable Long tripDayId) {
        tripDayService.delete(requireUserId(principal), tripId, tripDayId);
        return ResponseEntity.ok(ApiResponse.success("여행 일자가 삭제되었습니다.", null));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
