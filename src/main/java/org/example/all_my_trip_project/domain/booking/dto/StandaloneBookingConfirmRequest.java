package org.example.all_my_trip_project.domain.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.accommodation.dto.SaveAccommodationRequest;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickRequest;

import java.util.List;

/** 여행 생성 전에 사용자가 확정한 왕복 항공과 숙소 스냅샷. */
public record StandaloneBookingConfirmRequest(
        @NotNull @Size(min = 2, max = 2) List<@Valid OutboundClickRequest> flights,
        @NotNull @Valid SaveAccommodationRequest accommodation
) {}
