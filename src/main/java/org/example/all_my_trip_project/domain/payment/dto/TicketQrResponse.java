package org.example.all_my_trip_project.domain.payment.dto;

import java.time.OffsetDateTime;

/**
 * QR에 담을 입장 코드. 이 응답에만 평문이 실린다. (#265)
 *
 * <p>서버는 해시만 저장하므로 이 값을 다시 받아올 수 없다. 화면이 QR을 그린 뒤 버려야 한다.
 *
 * <p>{@code serverTime}을 함께 내리는 이유는 남은 시간을 손님 시계로 계산하면 안 되기
 * 때문이다. 시계가 틀어진 기기에서는 아직 유효한 QR을 만료로 표시하거나 그 반대가 된다.
 */
public record TicketQrResponse(
        Long issuedTicketId,
        String ticketNumber,
        String token,
        OffsetDateTime expiresAt,
        OffsetDateTime serverTime
) {}
