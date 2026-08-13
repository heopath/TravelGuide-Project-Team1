package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 판매 상태 변경 요청.
 *
 * <p>허용값은 {@code ck_ticket_products_status} 제약과 같게 유지한다. 한쪽만 늘리면
 * 화면에서는 통과했는데 DB가 거부하는 형태로 실패한다.
 */
public record AdminTicketProductStatusRequest(
        @NotBlank(message = "판매 상태를 선택해 주세요.")
        @Pattern(regexp = "DRAFT|ON_SALE|SOLD_OUT|ENDED|CANCELLED",
                message = "올바른 판매 상태가 아닙니다.")
        String status
) {}
