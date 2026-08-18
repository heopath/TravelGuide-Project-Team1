package org.example.all_my_trip_project.domain.ticket.dto;

import java.util.List;

/**
 * 시간대 등록 결과.
 *
 * <p>{@code skipped}를 함께 돌려주는 이유는 반복 등록에서 이미 있는 날을 건너뛰기 때문이다.
 * 30일을 요청했는데 5일만 만들어졌을 때 그 사실을 안 알려주면 관리자는 전부 열렸다고 믿는다.
 */
public record AdminTicketSlotCreateResponse(
        int created,
        int skipped,
        List<AdminTicketSlotDTO> slots
) {}
