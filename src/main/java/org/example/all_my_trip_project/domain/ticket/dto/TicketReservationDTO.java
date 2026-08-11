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
public class TicketReservationDTO {
    private Long reservationId;
    private String reservationNumber;
    private Long tripId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String requestKey;
    private Long slotId;
    private String productName;
    private String optionName;
    private LocalDate usageDate;
    private LocalTime usageStartTime;
    private Integer quantity;
    private BigDecimal unitPrice;
}
