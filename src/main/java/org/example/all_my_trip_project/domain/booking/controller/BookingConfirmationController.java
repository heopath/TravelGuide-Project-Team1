package org.example.all_my_trip_project.domain.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.BookingConfirmationResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingConfirmationService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/booking-confirmation")
@RequiredArgsConstructor
public class BookingConfirmationController {

    private final BookingConfirmationService bookingConfirmationService;

    @GetMapping
    public ApiResponse<BookingConfirmationResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(bookingConfirmationService.get(requireUserId(principal), tripId));
    }

    @PostMapping
    public ApiResponse<BookingConfirmationResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success("예약을 확정했습니다.",
                bookingConfirmationService.confirm(requireUserId(principal), tripId));
    }

    @DeleteMapping
    public ApiResponse<BookingConfirmationResponse> clear(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success("예약 확정을 해제했습니다.",
                bookingConfirmationService.clear(requireUserId(principal), tripId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
