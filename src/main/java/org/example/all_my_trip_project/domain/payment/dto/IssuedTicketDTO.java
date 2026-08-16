package org.example.all_my_trip_project.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 발급된 티켓 한 장.
 *
 * <p>검증 토큰은 <b>해시만</b> 저장한다. 원문은 발급 응답에 한 번 실어 보내고 서버에 남기지
 * 않는다. DB가 새더라도 그 값만으로 입장할 수 없어야 하기 때문이다. 그래서 이 DTO에도
 * 해시를 담지 않는다 — 조회 응답에 실려 나갈 이유가 없다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssuedTicketDTO {
    private Long issuedTicketId;
    private Long reservationItemId;
    private String ticketNumber;
    private String issueMethod;
    private String status;
    private OffsetDateTime validFrom;
    private OffsetDateTime validUntil;
    private OffsetDateTime issuedAt;

    /**
     * 발급 직후에만 채운다. 조회에서는 항상 비어 있다.
     * 화면이 QR이나 입장 코드로 보여줄 값이다.
     */
    private String verificationToken;
}
