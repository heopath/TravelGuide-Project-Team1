package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;
import org.example.all_my_trip_project.domain.booking.dto.BookingConfirmationResponse;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingConfirmationServiceTest {

    private TripDAO tripDAO;
    private FlightBookingService flightService;
    private AccommodationBookingService accommodationService;
    private BookingConfirmationService service;

    @BeforeEach
    void setUp() {
        tripDAO = mock(TripDAO.class);
        flightService = mock(FlightBookingService.class);
        accommodationService = mock(AccommodationBookingService.class);
        service = new BookingConfirmationService(tripDAO, flightService, accommodationService);
    }

    @Test
    void confirmsWhenRequiredSelectionsExistWithoutTicket() {
        TripDTO draft = TripDTO.builder().tripId(10L).userId(7L).build();
        TripDTO confirmed = TripDTO.builder().tripId(10L).userId(7L)
                .bookingConfirmedAt(OffsetDateTime.parse("2026-08-24T10:00:00+09:00"))
                .build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(draft), Optional.of(confirmed));
        when(tripDAO.confirmBooking(10L)).thenReturn(1);
        completeRequiredSelections();

        BookingConfirmationResponse result = service.confirm(7L, 10L);

        assertThat(result.confirmed()).isTrue();
        assertThat(result.eligible()).isTrue();
        assertThat(result.missingSteps()).isEmpty();
        assertThat(result.redirectUrl()).isEqualTo("/mypage?view=tickets");
        verify(tripDAO).confirmBooking(10L);
    }

    @Test
    void rejectsConfirmationAndListsMissingSelections() {
        when(tripDAO.findById(10L)).thenReturn(Optional.of(
                TripDTO.builder().tripId(10L).userId(7L).build()));
        when(flightService.getBookings(7L, 10L)).thenReturn(new TripFlightBookingsResponse(
                List.of(selectedFlight(0), FlightBookingLegResponse.empty(1)),
                null, true, false, null,
                new TripFlightBookingsResponse.Progress(0, 3), List.of()));
        when(accommodationService.getBookings(7L, 10L))
                .thenReturn(TripAccommodationsResponse.from(List.of()));
        BookingConfirmationResponse state = service.get(7L, 10L);

        assertThat(state.eligible()).isFalse();
        assertThat(state.missingSteps())
                .containsExactly("INBOUND_FLIGHT", "ACCOMMODATION");
        assertThatThrownBy(() -> service.confirm(7L, 10L))
                .isInstanceOf(BusinessException.class);
        verify(tripDAO, never()).confirmBooking(10L);
    }

    private void completeRequiredSelections() {
        when(flightService.getBookings(7L, 10L)).thenReturn(new TripFlightBookingsResponse(
                List.of(selectedFlight(0), selectedFlight(1)), null, true, false, null,
                new TripFlightBookingsResponse.Progress(0, 3), List.of()));
        TripAccommodationsResponse.Stay stay = new TripAccommodationsResponse.Stay(
                20L, "2026-08-28", "2026-08-30", 2, AccommodationBookingStatus.SELECTED,
                "hotel-1", "mock", "부산 호텔", "HOTEL", "부산", "부산", 4.5,
                null, null, "KRW", "MOCK", false, null);
        when(accommodationService.getBookings(7L, 10L)).thenReturn(
                new TripAccommodationsResponse(List.of(stay), null, true, false, "MOCK", List.of()));
    }

    private FlightBookingLegResponse selectedFlight(int leg) {
        return new FlightBookingLegResponse(
                leg, BookingStatus.NONE, "offer-" + leg, "KE", "대한항공", "KE100",
                "09:00", "10:00", null, "KRW", "PUBLISHED", null,
                leg == 0 ? "GMP" : "PUS", leg == 0 ? "PUS" : "GMP", null, null);
    }
}
