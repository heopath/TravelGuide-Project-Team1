package org.example.all_my_trip_project.domain.ticket.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 검표 결과.
 *
 * <p>실패도 200으로 돌려준다. 없는 코드나 이미 쓴 티켓은 <b>요청이 잘못된 것이 아니라 검표의
 * 정상적인 결과</b>다. 4xx로 돌려주면 화면이 오류 처리 경로로 빠져 "왜 안 되는지"를 보여주기
 * 어려워지고, 현장에서는 그 이유가 가장 중요하다.
 */
public record TicketValidationResponse(
        /** SUCCESS · NOT_FOUND · ALREADY_USED · CANCELLED · EXPIRED */
        String result,
        boolean admitted,
        /** 검표원이 손님에게 그대로 읽어 줄 수 있는 문장. */
        String message,

        /* 아래는 티켓을 찾았을 때만 채운다. NOT_FOUND면 전부 비어 있다. */
        String ticketNumber,
        String productName,
        String optionName,
        LocalDate usageDate,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        /** 이미 사용된 티켓이면 언제 썼는지. 중복 입장 시비를 가릴 때 필요하다. */
        OffsetDateTime usedAt
) {}
