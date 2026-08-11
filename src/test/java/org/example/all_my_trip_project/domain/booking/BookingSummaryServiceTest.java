package org.example.all_my_trip_project.domain.booking;

import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingSummaryService;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingSummaryServiceTest {

    private FlightBookingService flightService;
    private AccommodationBookingService accommodationService;
    private TicketService ticketService;
    private TripDAO tripDAO;
    private BookingSummaryService service;

    @BeforeEach
    void setUp() {
        flightService = mock(FlightBookingService.class);
        accommodationService = mock(AccommodationBookingService.class);
        ticketService = mock(TicketService.class);
        tripDAO = mock(TripDAO.class);
        service = new BookingSummaryService(flightService, accommodationService, ticketService, tripDAO);
        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L).userId(7L).build()));
    }

    @Test
    @DisplayName("세 종류 예약을 공통 목록으로 합치고 실습 금액을 따로 센다")
    void combinesThreeBookingTypes() {
        FlightBookingLegResponse outbound = new FlightBookingLegResponse(
                0, BookingStatus.USER_REPORTED, "offer-1", "KE", "대한항공", "KE100",
                "09:00", "10:00", amount("100000"), "KRW", "PUBLISHED", null);
        when(flightService.getBookings(7L, 10L)).thenReturn(new TripFlightBookingsResponse(
                List.of(outbound, FlightBookingLegResponse.empty(1)), amount("100000"),
                true, false, "PUBLISHED", new TripFlightBookingsResponse.Progress(0, 3), List.of()));

        TripAccommodationsResponse.Stay stay = new TripAccommodationsResponse.Stay(
                20L, "2026-08-17", "2026-08-19", 2, AccommodationBookingStatus.SELECTED,
                "hotel-1", "tourapi", "제주 호텔", "HOTEL", "제주시", "제주", 4.5,
                amount("100000"), amount("200000"), "KRW", "SANDBOX", true, null);
        when(accommodationService.getBookings(7L, 10L)).thenReturn(new TripAccommodationsResponse(
                List.of(stay), amount("200000"), true, false, "SANDBOX", List.of()));

        when(ticketService.reservations(7L, 10L)).thenReturn(List.of(TicketReservationDTO.builder()
                .reservationId(30L).tripId(10L).status("PENDING")
                .productName("아쿠아리움").optionName("성인").usageDate(LocalDate.of(2026, 8, 18))
                .quantity(2).totalAmount(amount("40000")).currency("KRW").build()));

        TripBookingSummaryResponse result = service.get(7L, 10L);

        assertThat(result.items()).hasSize(3);
        assertThat(result.money().estimatedTotal()).isEqualByComparingTo("340000");
        assertThat(result.money().practiceTotal()).isEqualByComparingTo("240000");
        assertThat(result.money().actualPaymentConfirmed()).isFalse();
        assertThat(result.progress().done()).isEqualTo(1);
    }

    @Test
    @DisplayName("항공 조회가 실패해도 숙소와 티켓 결과 형식은 유지한다")
    void reportsPartialFailure() {
        when(flightService.getBookings(7L, 10L)).thenThrow(new IllegalStateException("flight db down"));
        when(accommodationService.getBookings(7L, 10L)).thenReturn(TripAccommodationsResponse.from(List.of()));
        when(ticketService.reservations(7L, 10L)).thenReturn(List.of());

        TripBookingSummaryResponse result = service.get(7L, 10L);

        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).extracting(TripBookingSummaryResponse.SectionError::section)
                .containsExactly("FLIGHT");
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
