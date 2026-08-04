package org.example.all_my_trip_project.domain.trip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.trip.type.CompanionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TripCreateRequest(
        @Size(max = 150) String title,
        @NotBlank @Size(max = 150) String destinationName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull CompanionType companionType,
        @NotNull @Min(1) @Max(20) Integer companionCount,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal budgetAmount
) {
}
