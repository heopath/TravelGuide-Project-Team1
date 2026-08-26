package org.example.all_my_trip_project.domain.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.booking.dto.BookingConfirmationResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class BookingConfirmationService {

    private static final String MYPAGE_BOOKINGS = "/mypage?view=tickets";

    private final TripDAO tripDAO;
    private final FlightBookingService flightBookingService;
    private final AccommodationBookingService accommodationBookingService;

    @Transactional(readOnly = true)
    public BookingConfirmationResponse get(Long userId, Long tripId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        return response(trip, missingSteps(userId, tripId));
    }

    @Transactional
    public BookingConfirmationResponse confirm(Long userId, Long tripId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        List<String> missing = missingSteps(userId, tripId);
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOKING_SELECTION_INCOMPLETE);
        }
        if (trip.getBookingConfirmedAt() == null && tripDAO.confirmBooking(tripId) != 1) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        TripDTO confirmed = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        return response(confirmed, List.of());
    }

    @Transactional
    public BookingConfirmationResponse clear(Long userId, Long tripId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        if (trip.getBookingConfirmedAt() != null && tripDAO.clearBookingConfirmation(tripId) != 1) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        TripDTO cleared = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        return response(cleared, missingSteps(userId, tripId));
    }

    private List<String> missingSteps(Long userId, Long tripId) {
        List<String> missing = new ArrayList<>();
        TripFlightBookingsResponse flights = flightBookingService.getBookings(userId, tripId);
        boolean outbound = flights.legs().stream()
                .anyMatch(leg -> leg.leg() == FlightBookingService.OUTBOUND_LEG && leg.offerId() != null);
        boolean inbound = flights.legs().stream()
                .anyMatch(leg -> leg.leg() == FlightBookingService.INBOUND_LEG && leg.offerId() != null);
        if (!outbound) missing.add("OUTBOUND_FLIGHT");
        if (!inbound) missing.add("INBOUND_FLIGHT");
        if (accommodationBookingService.getBookings(userId, tripId).stays().isEmpty()) {
            missing.add("ACCOMMODATION");
        }
        return List.copyOf(missing);
    }

    private BookingConfirmationResponse response(TripDTO trip, List<String> missing) {
        return new BookingConfirmationResponse(
                trip.getBookingConfirmedAt() != null,
                missing.isEmpty(),
                trip.getBookingConfirmedAt(),
                missing,
                MYPAGE_BOOKINGS);
    }

    private TripDTO requireOwnedTrip(Long userId, Long tripId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }
}
