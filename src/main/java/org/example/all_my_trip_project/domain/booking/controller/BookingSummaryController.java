package org.example.all_my_trip_project.domain.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingSummaryService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/booking-summary")
@RequiredArgsConstructor
public class BookingSummaryController {

    private final BookingSummaryService bookingSummaryService;

    @GetMapping
    public ApiResponse<TripBookingSummaryResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(bookingSummaryService.get(principal.userId(), tripId));
    }
}
