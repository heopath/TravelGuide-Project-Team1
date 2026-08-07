package org.example.all_my_trip_project.domain.flight.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dto.BookingRefRequest;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickRequest;
import org.example.all_my_trip_project.domain.flight.dto.ReportBookedRequest;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}")
@RequiredArgsConstructor
public class FlightBookingController {

    private final FlightBookingService flightBookingService;

    /** 딥링크 클릭 기록. 응답의 clickId를 들고 있다가 복귀 시 결과를 붙인다. */
    @PostMapping("/flights/{leg}/outbound-click")
    public ApiResponse<Map<String, Long>> recordOutboundClick(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable int leg,
            @Valid @RequestBody OutboundClickRequest request) {
        Long clickId = flightBookingService.recordOutboundClick(
                requireUserId(principal), tripId, leg, request);
        return ApiResponse.success("항공편 선택을 저장했어요.", Map.of("clickId", clickId));
    }

    /** 자가 신고. 결제 확인이 아니다. */
    @PatchMapping("/flights/{leg}/report")
    public ApiResponse<TripFlightBookingsResponse> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable int leg,
            @Valid @RequestBody ReportBookedRequest request) {
        Long userId = requireUserId(principal);
        flightBookingService.reportBooked(userId, tripId, leg, request);
        return ApiResponse.success("일정에 반영했어요.", flightBookingService.getBookings(userId, tripId));
    }

    @PatchMapping("/flights/{leg}/booking-ref")
    public ApiResponse<TripFlightBookingsResponse> updateBookingRef(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable int leg,
            @Valid @RequestBody BookingRefRequest request) {
        Long userId = requireUserId(principal);
        flightBookingService.updateBookingRef(userId, tripId, leg, request);
        return ApiResponse.success("예약번호를 저장했어요.", flightBookingService.getBookings(userId, tripId));
    }

    /** 모달2의 "아니요, 다시 볼게요". */
    @DeleteMapping("/flights/{leg}")
    public ApiResponse<TripFlightBookingsResponse> cancelSelection(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable int leg,
            @RequestParam(required = false) Long clickId) {
        Long userId = requireUserId(principal);
        flightBookingService.cancelSelection(userId, tripId, leg, clickId);
        return ApiResponse.success("선택을 취소했어요.", flightBookingService.getBookings(userId, tripId));
    }

    /** 우측 예약 현황 패널과 `내 예약` 탭이 함께 쓰는 통합 조회. */
    @GetMapping("/bookings")
    public ApiResponse<TripFlightBookingsResponse> getBookings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(flightBookingService.getBookings(requireUserId(principal), tripId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
