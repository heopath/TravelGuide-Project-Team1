package org.example.all_my_trip_project.domain.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.MyBookingsResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingSummaryService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내가 예약한 항공·숙소·티켓을 여행에 상관없이 종류별로 본다.
 *
 * <p>여행별 요약({@code /api/v1/trips/{tripId}/booking-summary})은 "이 여행이 어떻게
 * 됐나"를 보는 길이다. 이쪽은 "내가 뭘 예약했나"를 보는 길이라 주소를 따로 둔다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/my-bookings")
@RequiredArgsConstructor
public class MyBookingsController {

    private final BookingSummaryService bookingSummaryService;

    @GetMapping
    public ApiResponse<MyBookingsResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            /* 비우면 전부. 화면의 `전체` 탭이 이 경우다. */
            @RequestParam(required = false) String type) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(bookingSummaryService.getAll(principal.userId(), type));
    }
}
