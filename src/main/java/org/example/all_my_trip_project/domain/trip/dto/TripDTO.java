package org.example.all_my_trip_project.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDTO {
    private Long tripId;
    private Long userId;
    private String title;
    private String destinationName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String companionType;
    private Integer companionCount;
    private String purpose;
    private BigDecimal budgetAmount;
    private String currencyCode;
    private String transportPreference;
    private String foodPreference;
    private String pace;
    private String accommodationStyle;
    private String status;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
