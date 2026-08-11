package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketReservationRequest(
        @NotNull Long tripId,
        @NotNull Long slotId,
        @NotNull @Min(1) @Max(10) Integer quantity,
        @NotBlank @Size(max = 100) String requestKey
) {}
