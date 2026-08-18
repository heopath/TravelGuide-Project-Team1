package org.example.all_my_trip_project.domain.ticket.dto;

import java.util.List;

/**
 * 상품 하나와 그 상품에서 고를 수 있는 시간대 전부.
 *
 * <p>시간대는 {@link TicketOfferDTO}를 그대로 쓴다. 예약 요청이 {@code slotId} 하나만
 * 필요로 하므로 목록과 상세가 같은 모양을 쓰는 편이 화면에서 갈라지지 않는다.
 */
public record TicketProductDetailDTO(
        TicketProductSummaryDTO product,
        List<TicketOfferDTO> slots
) {}
