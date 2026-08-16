package org.example.all_my_trip_project.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 결제 직전에 잠가 둔 예약. 결제 판단과 발권에 필요한 값만 담는다.
 *
 * <p>이용 일시를 함께 읽는 것은 발권 때문이다. 티켓의 유효기간이 이용일 기준이라, 결제와
 * 발권을 한 트랜잭션에서 끝내려면 이 시점에 알고 있어야 한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayableReservationDTO {
    private Long reservationId;
    private Long reservationItemId;
    private String status;
    private BigDecimal totalAmount;
    private String currencyCode;
    private OffsetDateTime expiresAt;
    private LocalDate usageDate;
    private LocalTime usageStartTime;
    private Integer quantity;
}
