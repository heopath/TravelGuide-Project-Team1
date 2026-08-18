package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 상품 하나에 속한 옵션. 가격과 1인 구매 한도를 들고 있다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketOptionDTO {
    private Long ticketProductOptionId;
    private Long ticketProductId;
    private String name;
    private String description;
    private BigDecimal unitPrice;
    private String currency;
    private Integer maxQuantityPerUser;
    private Integer sortOrder;
    private Boolean isActive;

    /**
     * 이 옵션에 달린 시간대 수. 0이면 옵션만 있고 팔 날짜가 없다는 뜻이라
     * 화면이 "시간대 없음"을 띄워야 한다.
     */
    private Integer slotCount;
}
