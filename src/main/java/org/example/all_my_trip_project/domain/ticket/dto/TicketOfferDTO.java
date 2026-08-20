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
public class TicketOfferDTO {
    private Long productId;
    private Long placeId;
    private String productName;
    private String description;
    private String placeName;
    private String region;
    private String city;
    private String imageUrl;
    private Long optionId;
    private String optionName;
    private Long slotId;
    private LocalDate usageDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal unitPrice;
    private String currency;
    private Integer maxQuantityPerUser;
    private Integer remainingQuantity;

    /**
     * 판매 유형과 지금 상태. (#256)
     *
     * <p>{@code saleType}은 {@code NORMAL} 또는 {@code SCHEDULED}, {@code saleState}는
     * {@code SCHEDULED}(오픈 전) / {@code ON_SALE} / {@code ENDED}다. 상태는 서버가 정한다 —
     * 손님 기기 시계는 몇 분씩 틀어져 있어 화면이 시각을 비교하면 아직 안 열린 상품을 살 수
     * 있는 것처럼 보여준다.
     *
     * <p>{@code opensAt}은 아직 안 열린 지정 시각 판매에만 있다.
     */
    private String saleType;
    private String saleState;
    private OffsetDateTime saleStartAt;
    private OffsetDateTime saleEndAt;
    private OffsetDateTime opensAt;
}
