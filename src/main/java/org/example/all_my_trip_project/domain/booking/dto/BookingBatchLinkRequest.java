package org.example.all_my_trip_project.domain.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingBatchLinkRequest(@NotNull @Positive Long tripId) {}
