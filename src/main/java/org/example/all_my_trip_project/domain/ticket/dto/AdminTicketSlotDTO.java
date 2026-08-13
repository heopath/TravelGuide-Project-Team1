package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 상품 하나에 속한 시간대와 그 재고. 재고 조정은 이 단위로만 한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketSlotDTO {
    private Long ticketTimeSlotId;
    private Long ticketProductOptionId;
    private String optionName;
    private BigDecimal unitPrice;
    private String currency;

    private LocalDate usageDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;

    private Integer totalQuantity;

    /** 예약 흐름이 관리하는 값. 관리자는 바꾸지 않는다. */
    private Integer reservedQuantity;

    private Integer remainingQuantity;

    /** 옵션이 비활성이면 판매 중이어도 예약 화면에 뜨지 않는다. 화면에서 이유를 밝히려고 함께 내린다. */
    private Boolean optionActive;
}
