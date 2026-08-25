package org.example.all_my_trip_project.domain.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.BookingMatchResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingMatchService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/booking-matches")
@RequiredArgsConstructor
public class BookingMatchController {

    private final BookingMatchService bookingMatchService;

    @GetMapping
    public ApiResponse<BookingMatchResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @RequestParam(required = false) UUID bookingBatchId) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(bookingMatchService.get(principal.userId(), tripId, bookingBatchId));
    }
}
