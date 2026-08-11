package org.example.all_my_trip_project.domain.accommodation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingRefRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.RecordAccommodationClickRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.ReportAccommodationBookedRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.SaveAccommodationRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 여행에 담아둔 숙소.
 *
 * <p>항공은 구간이 0/1로 고정이라 {@code /flights/{leg}}로 가리킬 수 있었지만,
 * 숙박은 건수가 가변이라 생성된 id로 식별한다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/accommodations")
@RequiredArgsConstructor
public class AccommodationBookingController {

    private final AccommodationBookingService accommodationBookingService;

    /** 숙소 선택을 여행에 저장한다. 같은 기간을 다시 고르면 교체된다. */
    @PostMapping
    public ApiResponse<TripAccommodationsResponse> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @Valid @RequestBody SaveAccommodationRequest request) {
        return ApiResponse.success("숙소를 담았어요.",
                accommodationBookingService.save(requireUserId(principal), tripId, request));
    }

    /**
     * 예약 사이트로 나간 것을 기록한다.
     *
     * <p>복귀 감지는 놓칠 수 있으므로, 놓친 건을 다음 방문에 다시 물어보려면 이 기록이 있어야 한다.
     * 기록에 실패해도 화면은 이동을 막지 않는다 — 사용자의 목적은 예약이다.
     */
    @PostMapping("/{accommodationBookingId}/outbound-click")
    public ApiResponse<Map<String, Long>> recordOutboundClick(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long accommodationBookingId,
            @RequestBody(required = false) RecordAccommodationClickRequest request) {
        Long clickId = accommodationBookingService.recordOutboundClick(
                requireUserId(principal), tripId, accommodationBookingId,
                request == null ? new RecordAccommodationClickRequest(null) : request);
        return ApiResponse.success(Map.of("clickId", clickId));
    }

    /** 자가 신고. 결제 확인이 아니라 사용자가 직접 표시한 값이다. */
    @PatchMapping("/{accommodationBookingId}/report")
    public ApiResponse<TripAccommodationsResponse> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long accommodationBookingId,
            @Valid @RequestBody ReportAccommodationBookedRequest request) {
        return ApiResponse.success("예약 상태를 기록했어요.",
                accommodationBookingService.reportBooked(
                        requireUserId(principal), tripId, accommodationBookingId, request));
    }

    /** 예약번호가 들어오면 확정으로 승격한다. 지우면 자가 신고 상태로 되돌아간다. */
    @PatchMapping("/{accommodationBookingId}/booking-ref")
    public ApiResponse<TripAccommodationsResponse> updateBookingRef(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long accommodationBookingId,
            @Valid @RequestBody AccommodationBookingRefRequest request) {
        return ApiResponse.success("예약번호를 저장했어요.",
                accommodationBookingService.updateBookingRef(
                        requireUserId(principal), tripId, accommodationBookingId, request));
    }

    @DeleteMapping("/{accommodationBookingId}")
    public ApiResponse<TripAccommodationsResponse> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long accommodationBookingId) {
        return ApiResponse.success("숙소를 뺐어요.",
                accommodationBookingService.remove(requireUserId(principal), tripId, accommodationBookingId));
    }

    @GetMapping
    public ApiResponse<TripAccommodationsResponse> getBookings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(
                accommodationBookingService.getBookings(requireUserId(principal), tripId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
