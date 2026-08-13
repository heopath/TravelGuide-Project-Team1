package org.example.all_my_trip_project.domain.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingQueueService;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/booking-queue/entries")
@RequiredArgsConstructor
public class BookingQueueController {

    private final BookingQueueService bookingQueueService;

    @PostMapping
    public ApiResponse<BookingQueueStatusResponse> enqueue(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTicketReservationRequest request
    ) {
        return ApiResponse.success(bookingQueueService.enqueue(requireUserId(principal), request));
    }

    @GetMapping("/{token}")
    public ApiResponse<BookingQueueStatusResponse> status(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String token
    ) {
        return ApiResponse.success(bookingQueueService.status(requireUserId(principal), token));
    }

    @PostMapping("/{token}/reservation")
    public ApiResponse<TicketReservationDTO> complete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String token
    ) {
        return ApiResponse.success("대기 순서에 따라 티켓을 모의 예약에 담았습니다.",
                bookingQueueService.complete(requireUserId(principal), token));
    }

    @DeleteMapping("/{token}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String token
    ) {
        bookingQueueService.cancel(requireUserId(principal), token);
        return ApiResponse.success("대기열에서 나왔습니다.", null);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
