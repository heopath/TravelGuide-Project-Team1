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

    /**
     * 판매 유형과 지금 상태. (#256)
     *
     * <p>{@code saleType}이 {@code SCHEDULED}면 정해진 시각에 열리는 상품이라, 열리기 전에도
     * 목록에 나온다 — 미리 보여야 손님이 그 시각에 모인다. {@code saleState}는
     * {@code SCHEDULED}(오픈 전) / {@code ON_SALE} / {@code ENDED}다.
     *
     * <p>상태를 화면이 시각 비교로 정하지 않게 서버가 판단해 내린다. 손님 기기 시계는 몇 분씩
     * 틀어져 있어, 아직 안 열린 상품을 살 수 있는 것처럼 보여주거나 그 반대가 된다.
     *
     * <p>{@code opensAt}은 아직 안 열린 지정 시각 판매에만 채운다.
     */
    private String saleType;
    private String saleState;
    private OffsetDateTime opensAt;

    /** 실제로 살 수 있는 시간대가 있는 날의 처음과 끝. 상품에 적힌 이용 기간과 다를 수 있다. */
    private LocalDate firstUsageDate;
    private LocalDate lastUsageDate;

    /** 남은 자리가 있는 시간대 수. 0이면 목록에 올리지 않는다. */
    private Integer availableSlotCount;
    private Integer remainingQuantity;
}
