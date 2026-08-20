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

    /**
     * 예약 내역 화면이 티켓 한 장을 통째로 보여주기 위해 필요한 값들. (#281)
     *
     * <p>목록 조회에서만 채운다. 결제·취소 같은 쓰기 흐름은 이 값들을 쓰지 않아서,
     * 그쪽 조회까지 조인을 늘리면 잠금이 걸리는 범위만 넓어진다.
     *
     * <p>비어 있을 수 있다. 상품이나 장소는 예약 뒤에 지워질 수 있고, 결제 정보는
     * 결제 전 예약에 아예 없다. 화면은 빈 값을 그리지 않고 그 줄을 빼는 쪽으로 다룬다.
     */
    private LocalTime usageEndTime;
    private String placeName;
    private String paymentMethod;
    /** 간편결제 사업자까지 갈린다. {@code MOCK_KAKAO_PAY}처럼 온다. */
    private String paymentProvider;
    private OffsetDateTime paidAt;
}
