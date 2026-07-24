package org.example.all_my_trip_project.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
