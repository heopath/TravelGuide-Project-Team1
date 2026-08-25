package org.example.all_my_trip_project.domain.booking.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 베이직 화면의 여행 조건과 일치하는 항공·숙소 예약 후보. */
public record BookingMatchResponse(
        Criteria criteria,
        List<FlightMatch> flights,
        List<AccommodationMatch> accommodations
) {
    public record Criteria(
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String destinationAirport
    ) {}

    public record FlightMatch(
            int leg,
            String title,
            String detail,
            String status,
            String statusLabel,
            String bookingRef,
            String origin,
            String destination,
            OffsetDateTime departureAt,
            OffsetDateTime arrivalAt,
            int matchScore
    ) {}

    public record AccommodationMatch(
            Long accommodationBookingId,
            String name,
            String detail,
            String status,
            String statusLabel,
            String bookingRef,
            String areaLabel,
            String address,
            String checkIn,
            String checkOut,
            int matchScore
    ) {}
}
