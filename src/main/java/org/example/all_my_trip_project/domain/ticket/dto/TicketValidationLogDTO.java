package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 검표 기록 한 줄.
 *
 * <p>{@code presentedTokenFingerprint}는 목록에 내리지 않는다. 화면이 쓸 일이 없고, 같은 코드로
 * 반복 시도한 것을 묶어 보는 것은 별도 조회로 할 일이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketValidationLogDTO {
    private Long ticketValidationLogId;
    private Long issuedTicketId;
    private String ticketNumber;
    private String productName;
    private Long validatorUserId;
    private String validatorNickname;
    private String validationResult;
    private String validationChannel;
    private String failureReason;
    private OffsetDateTime validatedAt;
}
