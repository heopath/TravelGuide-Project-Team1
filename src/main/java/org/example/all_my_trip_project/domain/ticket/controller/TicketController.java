package org.example.all_my_trip_project.domain.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/tickets")
    public ApiResponse<List<TicketOfferDTO>> search(
            @RequestParam(required = false, defaultValue = "") String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(ticketService.search(destination, from, to));
    }

    @PostMapping("/ticket-reservations")
    public ApiResponse<TicketReservationDTO> reserve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTicketReservationRequest request) {
        return ApiResponse.success("실습용 티켓을 예약 목록에 담았습니다.",
                ticketService.reserve(requireUserId(principal), request));
    }

    @GetMapping("/ticket-reservations")
    public ApiResponse<List<TicketReservationDTO>> reservations(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long tripId) {
        return ApiResponse.success(ticketService.reservations(requireUserId(principal), tripId));
    }

    @DeleteMapping("/ticket-reservations/{reservationId}")
    public ApiResponse<TicketReservationDTO> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId) {
        return ApiResponse.success("모의 예약을 취소하고 수량을 다시 열었습니다.",
                ticketService.cancel(requireUserId(principal), reservationId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
