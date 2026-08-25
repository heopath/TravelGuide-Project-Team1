package org.example.all_my_trip_project.domain.booking;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse;
import org.example.all_my_trip_project.domain.booking.service.BookingSummaryService;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
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
import java.time.OffsetDateTime;
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
                "09:00", "10:00", amount("100000"), "KRW", "PUBLISHED", null,
                "GMP", "CJU", null, null);
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

    @Test
    @DisplayName("최종 확정 전 항공과 숙소는 마이페이지 예약 내역에 노출하지 않는다")
    void hidesTripBookingsBeforeFinalConfirmation() {
        when(tripDAO.findByUserId(7L)).thenReturn(List.of(
                TripDTO.builder().tripId(10L).userId(7L).title("부산 여행").build()));
        when(ticketService.reservations(7L, null)).thenReturn(List.of());

        org.example.all_my_trip_project.domain.booking.dto.MyBookingsResponse result =
                service.getAll(7L, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.counts().total()).isZero();
    }

    @Test
    @DisplayName("최종 확정한 여행의 항공과 숙소는 예약 확정 상태로 표시한다")
    void exposesConfirmedTripBookingsInMyPage() {
        TripDTO trip = TripDTO.builder().tripId(10L).userId(7L).title("부산 여행")
                .bookingConfirmedAt(OffsetDateTime.parse("2026-08-24T10:00:00+09:00"))
                .build();
        when(tripDAO.findByUserId(7L)).thenReturn(List.of(trip));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        FlightBookingLegResponse outbound = new FlightBookingLegResponse(
                0, BookingStatus.NONE, "offer-1", "KE", "대한항공", "KE100",
                "09:00", "10:00", amount("100000"), "KRW", "PUBLISHED", null,
                "GMP", "PUS", null, null);
        when(flightService.getBookings(7L, 10L)).thenReturn(new TripFlightBookingsResponse(
                List.of(outbound, FlightBookingLegResponse.empty(1)), amount("100000"),
                true, false, "PUBLISHED", new TripFlightBookingsResponse.Progress(0, 3), List.of()));
        TripAccommodationsResponse.Stay stay = new TripAccommodationsResponse.Stay(
                20L, "2026-08-28", "2026-08-30", 2, AccommodationBookingStatus.SELECTED,
                "hotel-1", "mock", "부산 호텔", "HOTEL", "부산", "부산", 4.5,
                amount("100000"), amount("200000"), "KRW", "MOCK", true, null);
        when(accommodationService.getBookings(7L, 10L)).thenReturn(
                new TripAccommodationsResponse(List.of(stay), amount("200000"),
                        true, false, "MOCK", List.of()));
        when(ticketService.reservations(7L, 10L)).thenReturn(List.of());
        when(ticketService.reservations(7L, null)).thenReturn(List.of());

        org.example.all_my_trip_project.domain.booking.dto.MyBookingsResponse result =
                service.getAll(7L, null);

        assertThat(result.items()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo("CONFIRMED");
                    assertThat(item.statusLabel()).isEqualTo("예약 확정");
                });
    }

    @Test
    @DisplayName("여행 생성 전 확정 예약은 예약 내역에만 미연결 상태로 표시한다")
    void exposesStandaloneBookingsOnlyInTheGlobalBookingList() {
        when(tripDAO.findByUserId(7L)).thenReturn(List.of());
        when(ticketService.reservations(7L, null)).thenReturn(List.of());
        FlightBookingDTO flight = FlightBookingDTO.builder()
                .flightBookingId(31L).userId(7L).leg(0)
                .carrierName("대한항공").flightNumber("KE100")
                .origin("GMP").destination("PUS")
                .departureAt(OffsetDateTime.parse("2026-08-28T09:00:00+09:00"))
                .arrivalAt(OffsetDateTime.parse("2026-08-28T10:00:00+09:00"))
                .updatedAt(OffsetDateTime.parse("2026-08-24T10:00:00+09:00"))
                .userReportedBooked(true).build();
        when(flightService.getUnlinkedConfirmed(7L)).thenReturn(List.of(flight));
        AccommodationBookingDTO stay = new AccommodationBookingDTO();
        stay.setAccommodationBookingId(41L);
        stay.setUserId(7L);
        stay.setName("부산 호텔");
        stay.setCheckIn(LocalDate.of(2026, 8, 28));
        stay.setCheckOut(LocalDate.of(2026, 8, 30));
        stay.setUpdatedAt(OffsetDateTime.parse("2026-08-24T11:00:00+09:00"));
        stay.setUserReportedBooked(true);
        when(accommodationService.getUnlinkedConfirmed(7L)).thenReturn(List.of(stay));

        org.example.all_my_trip_project.domain.booking.dto.MyBookingsResponse result =
                service.getAll(7L, null);

        assertThat(result.items()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.tripId()).isNull();
                    assertThat(item.tripTitle()).isNull();
                    assertThat(item.statusLabel()).isEqualTo("예약 확정");
                });
        assertThat(result.items()).extracting(item -> item.type())
                .containsExactlyInAnyOrder("FLIGHT", "ACCOMMODATION");
        assertThat(result.items()).extracting(item -> item.type())
                .containsExactly("ACCOMMODATION", "FLIGHT");
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
