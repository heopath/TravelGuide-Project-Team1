package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 시간대 판매 상태 변경 요청.
 *
 * <p>시간대는 지우지 않고 닫는다. {@code reservation_items}가 시간대를 참조하고 있어
 * 지우면 이미 팔린 예약이 무엇이었는지 되짚을 수 없다.
 */
public record AdminTicketSlotStatusRequest(
        @NotBlank(message = "상태를 선택해 주세요.")
        String status
) {}
