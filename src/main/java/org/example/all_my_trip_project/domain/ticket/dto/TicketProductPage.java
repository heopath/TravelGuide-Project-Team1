package org.example.all_my_trip_project.domain.ticket.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 티켓 상품 목록 한 쪽. 관리자 목록({@code AdminTicketProductPage})과 같은 모양이다.
 *
 * <p>{@code serverTime}을 함께 내린다. 오픈까지 남은 시간을 손님 기기 시계로 세면 시계가
 * 틀어진 사람은 일찍 눌러 실패하거나 늦게 눌러 놓친다. 화면은 서버가 준 두 시각의 차이로
 * 센다. (#256)
 */
public record TicketProductPage(
        List<TicketProductSummaryDTO> items,
        int page,
        int size,
        long total,
        int totalPages,
        OffsetDateTime serverTime
) {}
