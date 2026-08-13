package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 시간대 재고 조정 요청.
 *
 * <p>{@code reservedQuantity}는 받지 않는다. 예약 흐름이 관리하는 값이라 관리자가 직접 고치면
 * 실제 예약 건수와 어긋나 남은 수량이 틀어진다.
 */
public record AdminTicketInventoryRequest(
        @NotNull(message = "전체 수량을 입력해 주세요.")
        @Min(value = 0, message = "전체 수량은 0 이상이어야 합니다.")
        @Max(value = 100000, message = "전체 수량이 너무 큽니다.")
        Integer totalQuantity
) {}
