package org.example.all_my_trip_project.domain.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.booking.dto.MyBookingsResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 여행에 상관없이 내가 예약한 것 전부.
     *
     * <p>여행별 요약이 "이 여행이 어떻게 됐나"라면 이쪽은 "내가 뭘 예약했나"다. 같은 자료를
     * 다르게 묶을 뿐이라 {@link #get}을 여행마다 부른다. 여행 수만큼 조회가 늘지만, 종류별
     * 전용 질의를 세 벌 더 만들어 두 곳이 서로 어긋나는 편보다 낫다.
     *
     * <p>한 여행을 못 읽어도 나머지는 보여준다. 하나 때문에 목록 전체가 비면 안 된다.
     *
     * @param type FLIGHT·HOTEL·TICKET 중 하나. 비우면 전부.
     */
    public MyBookingsResponse getAll(Long userId, String type) {
        String wanted = type == null || type.isBlank() ? null : type.trim().toUpperCase();

        Map<Long, String> tripTitles = new LinkedHashMap<>();
        for (TripDTO trip : tripDAO.findByUserId(userId)) {
            tripTitles.put(trip.getTripId(), trip.getTitle());
        }

        List<MyBookingsResponse.Entry> entries = new ArrayList<>();

        /*
         * 항공과 숙소는 여행에만 붙는다. 여행을 하나씩 훑는다. 한 여행을 못 읽어도 나머지는
         * 보여준다 — 하나 때문에 목록 전체가 비면 안 된다.
         */
        for (Map.Entry<Long, String> trip : tripTitles.entrySet()) {
            TripBookingSummaryResponse summary;
            try {
                summary = get(userId, trip.getKey());
            } catch (RuntimeException exception) {
                log.warn("예약 목록에서 여행 하나를 건너뜁니다. tripId={} type={}",
                        trip.getKey(), exception.getClass().getSimpleName());
                continue;
            }
            for (BookingItem item : summary.items()) {
                /* 티켓은 아래에서 한 번에 받는다. 여기서 담으면 두 번 들어간다. */
                if ("TICKET".equals(item.type())) continue;
                entries.add(entryOf(trip.getKey(), trip.getValue(), item));
            }
        }

        /*
         * 티켓은 여행에 붙지 않은 것도 있다. (#255) 여행만 훑으면 그런 티켓이 통째로
         * 빠져, 분명히 산 티켓인데 목록에 없는 일이 생긴다.
         */
        try {
            for (TicketReservationDTO ticket : ticketService.reservations(userId, null)) {
                entries.add(entryOf(ticket.getTripId(), tripTitles.get(ticket.getTripId()),
                        ticketItem(ticket)));
            }
        } catch (RuntimeException exception) {
            log.warn("예약 목록에서 티켓을 건너뜁니다. type={}", exception.getClass().getSimpleName());
        }

        /*
         * 개수는 고른 종류와 무관하게 전부 센다. 탭에 붙는 숫자라, 항공만 보고 있을 때도
         * 숙소가 몇 건인지 보여야 넘어갈 이유가 생긴다. 그래서 거르기 전에 센다.
         */
        MyBookingsResponse.Counts counts = countOf(entries);

        List<MyBookingsResponse.Entry> shown = wanted == null
                ? entries
                : entries.stream().filter(entry -> wanted.equals(entry.type())).toList();

        return new MyBookingsResponse(shown, counts);
    }

    private MyBookingsResponse.Entry entryOf(Long tripId, String tripTitle, BookingItem item) {
        return new MyBookingsResponse.Entry(
                tripId, tripTitle,
                item.type(), item.referenceId(), item.title(), item.detail(),
                item.status(), item.statusLabel(), item.amount(), item.currency(),
                item.practice(), item.usageDate(), item.quantity());
    }

    private MyBookingsResponse.Counts countOf(List<MyBookingsResponse.Entry> entries) {
        int flight = 0;
        int hotel = 0;
        int ticket = 0;
        for (MyBookingsResponse.Entry entry : entries) {
            if ("FLIGHT".equals(entry.type())) flight++;
            /* 숙소의 종류 이름은 ACCOMMODATION이다. HOTEL로 세면 늘 0이 나온다. */
            else if ("ACCOMMODATION".equals(entry.type())) hotel++;
            else if ("TICKET".equals(entry.type())) ticket++;
        }
        return new MyBookingsResponse.Counts(entries.size(), flight, hotel, ticket);
    }

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

    /**
     * 결제 전과 후를 반드시 구분한다.
     *
     * <p>{@code CONFIRMED}까지 "모의 예약"으로 보이면, 결제하고 티켓까지 받은 사람이 아직
     * 결제하지 않은 것으로 읽는다. {@code PENDING}은 반대로 자리를 잡아 두었을 뿐이고 시간이
     * 지나면 반납된다는 것이 드러나야 한다.
     */
    private String ticketStatus(String status) {
        return switch (Objects.requireNonNullElse(status, "")) {
            case "CANCELLED" -> "취소됨";
            case "EXPIRED" -> "만료됨";
            case "USED" -> "사용 완료";
            case "CONFIRMED" -> "결제 완료";
            case "PENDING" -> "결제 대기";
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
