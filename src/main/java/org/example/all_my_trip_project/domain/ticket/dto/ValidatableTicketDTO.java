package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 검표 직전에 잠가 둔 티켓. 판단에 필요한 값만 담는다.
 *
 * <p>예약 상태를 함께 읽는 것은 티켓만 봐서는 알 수 없기 때문이다. 예약이 취소되면 그에 딸린
 * 티켓도 쓸 수 없어야 하는데, 지금 취소 경로는 티켓 상태까지 바꾸지 않는다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatableTicketDTO {
    private Long issuedTicketId;
    private String ticketNumber;
    private String status;
    private OffsetDateTime validFrom;
    private OffsetDateTime validUntil;
    private OffsetDateTime usedAt;
    private String productName;
    private String optionName;
    private LocalDate usageDate;
    private String reservationStatus;
}
