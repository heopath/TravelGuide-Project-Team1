package org.example.all_my_trip_project.domain.ticket.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.TicketCancelResponse;
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
import org.example.all_my_trip_project.domain.ticket.dto.LinkTicketTripRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * 예약 목록. {@code tripId}를 주면 그 여행의 티켓만, 안 주면 산 티켓 전체다.
     *
     * <p>여행에 붙지 않은 티켓이 생기면서 "여행별"만으로는 다 볼 수 없게 됐다. (#255)
     */
    @GetMapping("/ticket-reservations")
    public ApiResponse<List<TicketReservationDTO>> reservations(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Long tripId) {
        return ApiResponse.success(ticketService.reservations(requireUserId(principal), tripId));
    }

    /**
     * 산 티켓을 여행에 붙이거나 뗀다. 본문의 {@code tripId}가 {@code null}이면 뗀다.
     *
     * <p>구매와 분리한 이유는 티켓을 살 때는 어느 여행에 쓸지 아직 안 정한 경우가 많기
     * 때문이다. 사고 나서 일정을 짜는 순서가 자연스럽다.
     */
    @PatchMapping("/ticket-reservations/{reservationId}/trip")
    public ApiResponse<TicketReservationDTO> linkTrip(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId,
            @RequestBody LinkTicketTripRequest request) {
        TicketReservationDTO result =
                ticketService.linkTrip(requireUserId(principal), reservationId, request.tripId());
        return ApiResponse.success(
                request.tripId() == null ? "여행 연결을 해제했습니다." : "여행에 연결했습니다.", result);
    }

    /**
     * 예약 취소. 결제 전이면 자리만 놓고, 결제 후면 환불까지 한다.
     *
     * <p>손님에게는 둘 다 "취소" 하나라 엔드포인트를 나누지 않는다. 결과 안내만 갈린다.
     */
    @DeleteMapping("/ticket-reservations/{reservationId}")
    public ApiResponse<TicketCancelResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId) {
        TicketCancelResponse result = ticketService.cancel(requireUserId(principal), reservationId);
        return ApiResponse.success(
                result.refunded()
                        ? "결제를 취소하고 발급된 티켓을 무효 처리했습니다. 수량도 다시 열었습니다."
                        : "모의 예약을 취소하고 수량을 다시 열었습니다.",
                result);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
