package org.example.all_my_trip_project.domain.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse.BookingItem;
import org.example.all_my_trip_project.domain.booking.dto.TripBookingSummaryResponse.SectionError;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class BookingSummaryService {

    private static final String KRW = "KRW";
    private static final int PROGRESS_TOTAL = 3;

    private final FlightBookingService flightBookingService;
    private final AccommodationBookingService accommodationBookingService;
    private final TicketService ticketService;
    private final TripDAO tripDAO;

    /**
     * 소유권은 먼저 한 번 확인한다. 그 뒤 각 종류는 따로 조회해 한 종류의 장애가
     * 나머지 두 종류까지 가리지 않게 한다.
     */
    public TripBookingSummaryResponse get(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);

        List<BookingItem> items = new ArrayList<>();
        List<SectionError> errors = new ArrayList<>();
        int done = 0;

        try {
            TripFlightBookingsResponse flights = flightBookingService.getBookings(userId, tripId);
            items.addAll(flights.legs().stream()
                    .filter(leg -> leg.offerId() != null)
                    .map(this::flightItem)
                    .toList());
            if (flights.airDone()) done++;
        } catch (RuntimeException exception) {
            errors.add(failed("FLIGHT", "항공 예약 정보를 불러오지 못했습니다.", exception));
        }

        try {
            TripAccommodationsResponse accommodations = accommodationBookingService.getBookings(userId, tripId);
            items.addAll(accommodations.stays().stream().map(this::accommodationItem).toList());
            if (accommodations.done()) done++;
        } catch (RuntimeException exception) {
            errors.add(failed("ACCOMMODATION", "숙소 예약 정보를 불러오지 못했습니다.", exception));
        }

        try {
            List<TicketReservationDTO> tickets = ticketService.reservations(userId, tripId);
            items.addAll(tickets.stream().map(this::ticketItem).toList());
            if (tickets.stream().anyMatch(this::activeTicket)) done++;
        } catch (RuntimeException exception) {
            errors.add(failed("TICKET", "티켓·액티비티 예약 정보를 불러오지 못했습니다.", exception));
        }

        BigDecimal estimatedTotal = items.stream()
                .filter(BookingItem::includedInEstimate)
                .map(BookingItem::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal practiceTotal = items.stream()
                .filter(BookingItem::includedInEstimate)
                .filter(BookingItem::practice)
                .map(BookingItem::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int excluded = (int) items.stream()
                .filter(item -> item.amount() != null && !item.includedInEstimate())
                .count();

        return new TripBookingSummaryResponse(
                List.copyOf(items),
                new TripBookingSummaryResponse.MoneySummary(
                        estimatedTotal, practiceTotal, KRW, excluded, false),
                new TripBookingSummaryResponse.Progress(done, PROGRESS_TOTAL),
                List.copyOf(errors));
    }

    private BookingItem flightItem(FlightBookingLegResponse leg) {
        boolean included = positive(leg.totalPrice()) && KRW.equalsIgnoreCase(leg.currency());
        boolean practice = "MOCK".equalsIgnoreCase(leg.priceSource());
        String title = join(" ", leg.carrierName(), leg.flightNumber());
        String detail = join(" → ", leg.departureTime(), leg.arrivalTime());
        return new BookingItem(
                "FLIGHT", "flight:" + leg.leg(), leg.leg(), title, detail,
                leg.status().name(), switch (leg.status()) {
                    case CONFIRMED -> "확정";
                    case USER_REPORTED -> "예약함 (직접 표시)";
                    case NONE -> "선택만 함";
                },
                leg.totalPrice(), leg.currency(), leg.priceSource(), included, practice,
                leg.bookingRef(), null, null);
    }

    private BookingItem accommodationItem(TripAccommodationsResponse.Stay stay) {
        boolean included = stay.countedInTotal()
                && positive(stay.totalPrice())
                && KRW.equalsIgnoreCase(stay.currency());
        boolean practice = "MOCK".equalsIgnoreCase(stay.priceSource())
                || "SANDBOX".equalsIgnoreCase(stay.priceSource());
        return new BookingItem(
                "ACCOMMODATION", String.valueOf(stay.accommodationBookingId()), null,
                stay.name(), stay.checkIn() + " → " + stay.checkOut(),
                stay.status().name(), switch (stay.status()) {
                    case CONFIRMED -> "확정";
                    case USER_REPORTED -> "예약함 (직접 표시)";
                    case SELECTED -> "선택 완료";
                },
                stay.totalPrice(), stay.currency(), stay.priceSource(), included, practice,
                stay.bookingRef(), stay.checkIn(), null);
    }

    private BookingItem ticketItem(TicketReservationDTO ticket) {
        boolean active = activeTicket(ticket);
        boolean included = active && positive(ticket.getTotalAmount())
                && KRW.equalsIgnoreCase(ticket.getCurrency());
        return new BookingItem(
                "TICKET", String.valueOf(ticket.getReservationId()), null,
                ticket.getProductName(), ticket.getOptionName(),
                ticket.getStatus(), ticketStatus(ticket.getStatus()),
                ticket.getTotalAmount(), ticket.getCurrency(), "INTERNAL_MOCK",
                included, true, null,
                ticket.getUsageDate() == null ? null : ticket.getUsageDate().toString(),
                ticket.getQuantity());
    }

    private boolean activeTicket(TicketReservationDTO ticket) {
        return !List.of("CANCELLED", "EXPIRED").contains(ticket.getStatus());
    }

    private String ticketStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "CANCELLED" -> "취소됨";
            case "EXPIRED" -> "만료됨";
            case "USED" -> "사용 완료";
            default -> "모의 예약";
        };
    }

    private SectionError failed(String section, String message, RuntimeException exception) {
        log.warn("{} booking summary section failed", section, exception);
        return new SectionError(section, message);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String join(String delimiter, String first, String second) {
        if (first == null || first.isBlank()) return Objects.requireNonNullElse(second, "");
        if (second == null || second.isBlank()) return first;
        return first + delimiter + second;
    }

    private void requireOwnedTrip(Long userId, Long tripId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
    }
}
