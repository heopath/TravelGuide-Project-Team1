package org.example.all_my_trip_project.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryItemDTO {
    private Long itineraryItemId;
    private Long tripDayId;
    private Long placeId;
    private String itemType;
    private String title;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer sortOrder;
    private String memo;
    private BigDecimal estimatedCost;
    private String currencyCode;
    private String source;
    // PostgreSQL TIMESTAMPTZ의 UTC offset을 보존하기 위한 타입이며 DTO 필드명은 기존과 동일하다.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
