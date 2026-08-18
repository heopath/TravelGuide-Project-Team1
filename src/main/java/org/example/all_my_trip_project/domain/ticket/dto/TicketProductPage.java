package org.example.all_my_trip_project.domain.ticket.dto;

import java.util.List;

/** 티켓 상품 목록 한 쪽. 관리자 목록({@code AdminTicketProductPage})과 같은 모양이다. */
public record TicketProductPage(
        List<TicketProductSummaryDTO> items,
        int page,
        int size,
        long total,
        int totalPages
) {}
