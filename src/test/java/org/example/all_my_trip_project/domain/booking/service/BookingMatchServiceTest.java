package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;
import org.example.all_my_trip_project.domain.booking.dto.BookingMatchResponse;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingMatchServiceTest {

    private FlightBookingService flightService;
    private AccommodationBookingService accommodationService;
    private TripDAO tripDAO;
    private BookingMatchService service;

    @BeforeEach
    void setUp() {
        flightService = mock(FlightBookingService.class);
        accommodationService = mock(AccommodationBookingService.class);
        tripDAO = mock(TripDAO.class);
        service = new BookingMatchService(tripDAO, flightService, accommodationService);

        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L)
                .userId(7L)
                .destinationName("부산광역시")
                .startDate(LocalDate.of(2026, 8, 28))
                .endDate(LocalDate.of(2026, 8, 30))
                .bookingConfirmedAt(OffsetDateTime.parse("2026-08-24T10:00:00+09:00"))
                .build()));
    }

    @Test
    void returnsOnlyReservationsMatchingDestinationAndPeriod() {
        FlightBookingLegResponse matchingOutbound = flight(0, "GMP", "PUS",
                "2026-08-28T09:00:00+09:00", "2026-08-28T10:00:00+09:00");
        FlightBookingLegResponse wrongDestination = flight(1, "CJU", "GMP",
                "2026-08-30T19:00:00+09:00", "2026-08-30T20:00:00+09:00");
        when(flightService.getBookings(7L, 10L)).thenReturn(flights(matchingOutbound, wrongDestination));

        TripAccommodationsResponse.Stay matchingStay = stay("부산 해운대 호텔", "해운대구", "2026-08-28", "2026-08-30");
        TripAccommodationsResponse.Stay secondMatchingStay = stay("부산 광안리 호텔", "광안리", "2026-08-29", "2026-08-30");
        TripAccommodationsResponse.Stay outsideTrip = stay("부산 호텔", "서면", "2026-09-01", "2026-09-02");
        when(accommodationService.getBookings(7L, 10L)).thenReturn(
                new TripAccommodationsResponse(
                        List.of(matchingStay, secondMatchingStay, outsideTrip), null,
                        false, true, "PUBLISHED", List.of()));

        BookingMatchResponse result = service.get(7L, 10L);

        assertThat(result.criteria().destinationAirport()).isEqualTo("PUS");
        assertThat(result.flights()).extracting(BookingMatchResponse.FlightMatch::destination)
                .containsExactly("PUS");
        assertThat(result.accommodations()).hasSize(2)
                .extracting(BookingMatchResponse.AccommodationMatch::name)
                .containsExactly("부산 해운대 호텔", "부산 광안리 호텔");
    }

    @Test
    void doesNotReturnReservationsFromAnotherUsersTrip() {
        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L).userId(99L).destinationName("부산광역시")
                .startDate(LocalDate.of(2026, 8, 28)).endDate(LocalDate.of(2026, 8, 30)).build()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get(7L, 10L))
                .isInstanceOf(org.example.all_my_trip_project.global.exception.BusinessException.class);
    }

    @Test
    void returnsNoMatchesBeforeFinalConfirmation() {
        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L).userId(7L).destinationName("부산광역시")
                .startDate(LocalDate.of(2026, 8, 28)).endDate(LocalDate.of(2026, 8, 30)).build()));

        BookingMatchResponse result = service.get(7L, 10L);

        assertThat(result.flights()).isEmpty();
        assertThat(result.accommodations()).isEmpty();
    }

    @Test
    void previewsOnlyTheConfirmedStandaloneBatchBeforeTripSave() {
        UUID batchId = UUID.fromString("93f9c35d-9e23-43f4-9b1a-4180b12fc658");
        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L).userId(7L).destinationName("부산광역시")
                .startDate(LocalDate.of(2026, 8, 28)).endDate(LocalDate.of(2026, 8, 30)).build()));
        when(flightService.getByBatch(7L, batchId)).thenReturn(List.of(
                standaloneFlight(0, "GMP", "PUS", "2026-08-28T09:00:00+09:00"),
                standaloneFlight(1, "PUS", "GMP", "2026-08-30T19:00:00+09:00")));
        AccommodationBookingDTO stay = new AccommodationBookingDTO();
        stay.setAccommodationBookingId(20L);
        stay.setUserId(7L);
        stay.setBookingBatchId(batchId);
        stay.setName("부산 해운대 호텔");
        stay.setAreaLabel("부산광역시 해운대구");
        stay.setAddress("부산광역시 해운대구 해운대로");
        stay.setCheckIn(LocalDate.of(2026, 8, 28));
        stay.setCheckOut(LocalDate.of(2026, 8, 30));
        stay.setUserReportedBooked(true);
        when(accommodationService.getByBatch(7L, batchId)).thenReturn(List.of(stay));

        BookingMatchResponse result = service.get(7L, 10L, batchId);

        assertThat(result.flights()).hasSize(2);
        assertThat(result.accommodations()).extracting(BookingMatchResponse.AccommodationMatch::name)
                .containsExactly("부산 해운대 호텔");
    }

    private TripFlightBookingsResponse flights(FlightBookingLegResponse outbound,
                                                FlightBookingLegResponse inbound) {
        return new TripFlightBookingsResponse(
                List.of(outbound, inbound), null, true, false, null,
                new TripFlightBookingsResponse.Progress(0, 3), List.of());
    }

    private FlightBookingLegResponse flight(int leg, String origin, String destination,
                                            String departureAt, String arrivalAt) {
        return new FlightBookingLegResponse(
                leg, BookingStatus.CONFIRMED, "offer-" + leg, "KE", "대한항공", "KE" + leg,
                "09:00", "10:00", null, "KRW", "PUBLISHED", "ref-" + leg,
                origin, destination, OffsetDateTime.parse(departureAt), OffsetDateTime.parse(arrivalAt));
    }

    private FlightBookingDTO standaloneFlight(int leg, String origin, String destination,
                                               String departureAt) {
        return FlightBookingDTO.builder()
                .userId(7L).leg(leg).origin(origin).destination(destination)
                .carrierName("대한항공").flightNumber("KE" + leg)
                .departureAt(OffsetDateTime.parse(departureAt))
                .arrivalAt(OffsetDateTime.parse(departureAt).plusHours(1))
                .userReportedBooked(true)
                .build();
    }

    private TripAccommodationsResponse.Stay stay(String name, String area,
                                                  String checkIn, String checkOut) {
        return new TripAccommodationsResponse.Stay(
                20L, checkIn, checkOut, 2, AccommodationBookingStatus.CONFIRMED,
                "hotel-1", "provider", name, "HOTEL", area, area, 4.5,
                null, null, "KRW", "PUBLISHED", false, "hotel-ref");
    }
}
