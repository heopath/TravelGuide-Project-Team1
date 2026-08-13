package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 관리자 예약 상품·재고 목록의 한 행.
 *
 * <p>재고는 {@code ticket_inventory}가 시간대(slot)마다 따로 들고 있어서 상품 단위 값이 없다.
 * 목록에서 상품마다 시간대를 펼쳐 보게 하면 화면이 감당이 안 되므로, 상품에 속한 모든 시간대의
 * 수량을 합쳐 한 줄로 보여준다. 시간대별 조정은 상세에서 다룬다.
 *
 * <p>시간대가 하나도 없는 상품은 합계가 {@code null}이 아니라 0으로 내려간다. 화면에서
 * "재고 없음"과 "아직 안 만든 상품"을 구분해야 하므로 {@link #slotCount}를 함께 본다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketProductDTO {
    private Long ticketProductId;
    private Long placeId;
    private String placeName;
    private String region;
    private String city;
    private String name;
    private String status;
    private OffsetDateTime saleStartAt;
    private OffsetDateTime saleEndAt;
    private LocalDate usageStartDate;
    private LocalDate usageEndDate;

    /** 활성 옵션 수. 0이면 판매 상태를 올려도 노출되지 않는다. */
    private Integer optionCount;

    /** 열린 시간대 수. 0이면 재고 합계가 0인 것이 정상이다. */
    private Integer slotCount;

    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer remainingQuantity;

    private BigDecimal minUnitPrice;
    private String currency;
}
