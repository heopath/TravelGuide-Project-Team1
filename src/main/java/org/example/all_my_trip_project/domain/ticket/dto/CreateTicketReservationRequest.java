package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 티켓 예약 요청.
 *
 * <p>{@code tripId}는 <b>선택</b>이다. 티켓은 관리자가 열어두면 여행 계획과 상관없이
 * 살 수 있고, 산 뒤에 여행에 붙이는 것은 별도 동작이다({@code PATCH .../trip}). (#255)
 *
 * <p>보내면 그 여행에 붙이고, 그때는 이용일이 여행 기간 안이어야 한다. 안 보내면
 * 여행에 붙지 않은 티켓으로 남는다.
 */
public record CreateTicketReservationRequest(
        Long tripId,
        @NotNull Long slotId,
        @NotNull @Min(1) @Max(10) Integer quantity,
        @NotBlank @Size(max = 100) String requestKey
) {}
