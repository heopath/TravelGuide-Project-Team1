package org.example.all_my_trip_project.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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
}
