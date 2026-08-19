package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservationDTO {
    private Long reservationId;
    private String reservationNumber;
    private Long tripId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String requestKey;
    private Long slotId;
    private String productName;
    private String optionName;
    private LocalDate usageDate;
    private LocalTime usageStartTime;
    private Integer quantity;
    private BigDecimal unitPrice;

    /**
     * 결제하지 않으면 자리를 반납하는 시각. (#276)
     *
     * <p>화면이 남은 시간을 보여주려고 내린다. 안 보여주면 손님은 담아둔 예약이 왜 갑자기
     * 사라졌는지 알 수 없다. 결제가 끝난 예약({@code CONFIRMED})에는 의미가 없다.
     */
    private OffsetDateTime expiresAt;
}
