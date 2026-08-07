package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
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
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @PostMapping("/api/v1/trips")
    public ResponseEntity<ApiResponse<TripCreateResult>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TripCreateRequest request) {
        Long userId = requireUserId(principal);
        TripCreateResult result = tripService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.tripId()))
                .body(ApiResponse.success("여행과 날짜별 일정이 생성되었습니다.", result));
    }

    @PostMapping("/api/v1/trips/{tripId}/days")
    public ResponseEntity<ApiResponse<TripDayDTO>> createTripDay(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @PathVariable Long tripId,
        @RequestBody TripDayDTO tripDay
    ) {
        tripDay.setTripId(tripId);
        Long id = tripService.createDay(requireUserId(principal), tripDay);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + tripId + "/days/" + id))
                .body(ApiResponse.success("여행 일자가 생성되었습니다.", tripDay));
    }

    @GetMapping("/api/v1/trips/{tripId}/days")
    public ApiResponse<List<TripDayDTO>> getTripDays(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(tripService.getDays(requireUserId(principal), tripId));
    }

    @PutMapping("/api/v1/trips/{tripId}/days/{tripDayId}")
    public ApiResponse<TripDayDTO> updateTripDay(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long tripDayId,
            @RequestBody TripDayDTO tripDay) {
        tripDay.setTripId(tripId);
        tripDay.setTripDayId(tripDayId);
        tripService.updateDay(requireUserId(principal), tripDay);
        return ApiResponse.success("여행 일자가 수정되었습니다.", tripDay);
    }

    @DeleteMapping("/api/v1/trips/{tripId}/days/{tripDayId}")
    public ResponseEntity<ApiResponse<Void>> deleteTripDay(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long tripDayId) {
        tripService.deleteDay(requireUserId(principal), tripId, tripDayId);
        return ResponseEntity.ok(ApiResponse.success("여행 일자가 삭제되었습니다.", null));
    }

    @PostMapping("/api/v1/trip-days/{tripDayId}/items")
    public ResponseEntity<ApiResponse<ItineraryItemDTO>> createItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId,
            @RequestBody ItineraryItemDTO item) {
        Long id = tripService.createItem(requireUserId(principal), tripDayId, item);
        return ResponseEntity.created(URI.create("/api/v1/trip-days/" + tripDayId + "/items/" + id))
                .body(ApiResponse.success("일정 항목이 추가되었습니다.", item));
    }

    @GetMapping("/api/v1/trip-days/{tripDayId}/items")
    public ApiResponse<List<ItineraryItemDTO>> getItems(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId) {
        return ApiResponse.success(tripService.getItems(requireUserId(principal), tripDayId));
    }

    @PutMapping("/api/v1/trip-days/{tripDayId}/items/{itemId}")
    public ApiResponse<ItineraryItemDTO> updateItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId,
            @PathVariable Long itemId,
            @RequestBody ItineraryItemDTO item) {
        item.setTripDayId(tripDayId);
        item.setItineraryItemId(itemId);
        tripService.updateItem(requireUserId(principal), item);
        return ApiResponse.success("일정 항목이 수정되었습니다.", item);
    }

    @DeleteMapping("/api/v1/trip-days/{tripDayId}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId,
            @PathVariable Long itemId) {
        tripService.deleteItem(requireUserId(principal), tripDayId, itemId);
        return ResponseEntity.ok(ApiResponse.success("일정 항목이 삭제되었습니다.", null));
    }

    @GetMapping("/api/v1/trips/{tripId}")
    public ApiResponse<TripDTO> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long tripId) {
        return ApiResponse.success(tripService.get(requireUserId(principal), tripId));
    }

    @GetMapping("/api/v1/trips")
    public ApiResponse<List<TripDTO>> getByUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(tripService.getByUser(requireUserId(principal)));
    }

    @PutMapping("/api/v1/trips/{tripId}")
    public ApiResponse<TripDTO> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long tripId, @RequestBody TripDTO trip) {
        Long userId = requireUserId(principal);
        trip.setTripId(tripId);
        trip.setUserId(userId);
        tripService.update(userId, trip);
        return ApiResponse.success("여행 정보가 수정되었습니다.", tripService.get(userId, tripId));
    }

    @DeleteMapping("/api/v1/trips/{tripId}")
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
