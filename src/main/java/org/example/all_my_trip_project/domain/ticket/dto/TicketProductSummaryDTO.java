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
 * 판매 중인 티켓 상품 한 줄. 목록 화면이 쓴다.
 *
 * <p>날짜를 조건으로 받지 않는다. 관리자가 열어둔 상품을 먼저 보여주고, 언제 갈지는
 * 상품을 고른 다음 정한다. 여행 기간으로 먼저 거르면 팔고 있는 티켓인데도 화면에
 * 아무것도 안 뜨는 일이 생긴다. (#255)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketProductSummaryDTO {
    private Long productId;
    private String productName;
    private String description;
    private Long placeId;
    private String placeName;
    private String region;
    private String city;
    private String imageUrl;

    private BigDecimal minUnitPrice;
    private String currency;

    private OffsetDateTime saleStartAt;
    private OffsetDateTime saleEndAt;

    /** 실제로 살 수 있는 시간대가 있는 날의 처음과 끝. 상품에 적힌 이용 기간과 다를 수 있다. */
    private LocalDate firstUsageDate;
    private LocalDate lastUsageDate;

    /** 남은 자리가 있는 시간대 수. 0이면 목록에 올리지 않는다. */
    private Integer availableSlotCount;
    private Integer remainingQuantity;
}
