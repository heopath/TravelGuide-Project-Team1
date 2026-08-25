package org.example.all_my_trip_project.domain.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.BookingBatchLinkRequest;
import org.example.all_my_trip_project.domain.booking.dto.BookingBatchResponse;
import org.example.all_my_trip_project.domain.booking.dto.StandaloneBookingConfirmRequest;
import org.example.all_my_trip_project.domain.booking.service.StandaloneBookingService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/booking-batches/{bookingBatchId}")
@RequiredArgsConstructor
public class StandaloneBookingController {

    private final StandaloneBookingService standaloneBookingService;

    @PostMapping("/confirmation")
    public ApiResponse<BookingBatchResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID bookingBatchId,
            @Valid @RequestBody StandaloneBookingConfirmRequest request) {
        return ApiResponse.success("항공·숙소 예약을 확정했습니다.",
                standaloneBookingService.confirm(requireUserId(principal), bookingBatchId, request));
    }

    @PatchMapping("/trip")
    public ApiResponse<BookingBatchResponse> link(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID bookingBatchId,
            @Valid @RequestBody BookingBatchLinkRequest request) {
        return ApiResponse.success("예약을 여행 일정에 연결했습니다.",
                standaloneBookingService.link(requireUserId(principal), bookingBatchId, request.tripId()));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
