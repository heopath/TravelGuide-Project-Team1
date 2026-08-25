package org.example.all_my_trip_project.domain.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.domain.booking.dto.BookingMatchResponse;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightBookingService;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 베이직 화면의 목적지·기간과 예약 스냅샷을 비교한다. */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class BookingMatchService {

    private static final Map<String, String> DESTINATION_AIRPORTS = Map.ofEntries(
            Map.entry("서귀포", "CJU"), Map.entry("제주", "CJU"),
            Map.entry("부산", "PUS"), Map.entry("김해", "PUS"),
            Map.entry("인천", "ICN"), Map.entry("김포", "GMP"), Map.entry("서울", "GMP"),
            Map.entry("대구", "TAE"), Map.entry("경주", "KPO"), Map.entry("포항", "KPO"),
            Map.entry("광주", "KWJ"), Map.entry("목포", "MWX"), Map.entry("무안", "MWX"),
            Map.entry("전남", "MWX"), Map.entry("순천", "RSU"), Map.entry("여수", "RSU"),
            Map.entry("청주", "CJJ"), Map.entry("충북", "CJJ"), Map.entry("충청북도", "CJJ"),
            Map.entry("울산", "USN"), Map.entry("진주", "HIN"), Map.entry("사천", "HIN"),
            Map.entry("군산", "KUV"), Map.entry("전북", "KUV"), Map.entry("전라북도", "KUV"),
            Map.entry("속초", "YNY"), Map.entry("강릉", "YNY"), Map.entry("양양", "YNY"),
            Map.entry("원주", "WJU"), Map.entry("강원", "YNY")
    );

    private final TripDAO tripDAO;
    private final FlightBookingService flightBookingService;
    private final AccommodationBookingService accommodationBookingService;

    public BookingMatchResponse get(Long userId, Long tripId) {
        return get(userId, tripId, null);
    }

    public BookingMatchResponse get(Long userId, Long tripId, UUID bookingBatchId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        String destinationAirport = airportOf(trip.getDestinationName());
        BookingMatchResponse.Criteria criteria = new BookingMatchResponse.Criteria(
                trip.getDestinationName(), trip.getStartDate(), trip.getEndDate(), destinationAirport);

        /*
         * 여행 저장 전에는 trip_id를 붙이지 않는다. 대신 예약 화면에서 확정한 묶음 UUID를
         * 가진 현재 브라우저 흐름에 한해서만 후보를 보여준다. 여행을 최종 저장하면 같은 행에
         * trip_id가 연결되고, 이후에는 아래의 일반 여행별 조회를 사용한다.
         */
        if (trip.getBookingConfirmedAt() == null) {
            if (bookingBatchId == null) {
                return new BookingMatchResponse(criteria, List.of(), List.of());
            }

            List<BookingMatchResponse.FlightMatch> flightMatches =
                    flightBookingService.getByBatch(userId, bookingBatchId).stream()
                            .map(flight -> flightMatch(flight, trip, destinationAirport))
                            .flatMap(Optional::stream)
                            .sorted(Comparator.comparingInt(BookingMatchResponse.FlightMatch::leg))
                            .toList();
            List<BookingMatchResponse.AccommodationMatch> accommodationMatches =
                    accommodationBookingService.getByBatch(userId, bookingBatchId).stream()
                            .map(stay -> accommodationMatch(stay, trip))
                            .flatMap(Optional::stream)
                            .sorted(Comparator.comparingInt(BookingMatchResponse.AccommodationMatch::matchScore)
                                    .reversed()
                                    .thenComparing(BookingMatchResponse.AccommodationMatch::checkIn))
                            .toList();
            return new BookingMatchResponse(criteria, flightMatches, accommodationMatches);
        }

        TripFlightBookingsResponse flights = flightBookingService.getBookings(userId, tripId);
        List<BookingMatchResponse.FlightMatch> flightMatches = flights.legs().stream()
                .map(leg -> flightMatch(leg, trip, destinationAirport))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(BookingMatchResponse.FlightMatch::leg))
                .toList();

        TripAccommodationsResponse accommodations = accommodationBookingService.getBookings(userId, tripId);
        List<BookingMatchResponse.AccommodationMatch> accommodationMatches = accommodations.stays().stream()
                .map(stay -> accommodationMatch(stay, trip))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(BookingMatchResponse.AccommodationMatch::matchScore)
                        .reversed()
                        .thenComparing(BookingMatchResponse.AccommodationMatch::checkIn))
                .toList();

        return new BookingMatchResponse(
                criteria,
                flightMatches,
                accommodationMatches);
    }

    private Optional<BookingMatchResponse.FlightMatch> flightMatch(
            FlightBookingLegResponse leg, TripDTO trip, String destinationAirport) {
        if (leg.origin() == null || leg.destination() == null || destinationAirport == null) {
            return Optional.empty();
        }

        boolean airportMatches = leg.leg() == FlightBookingService.OUTBOUND_LEG
                ? destinationAirport.equalsIgnoreCase(leg.destination())
                : destinationAirport.equalsIgnoreCase(leg.origin());
        LocalDate targetDate = leg.leg() == FlightBookingService.OUTBOUND_LEG
                ? trip.getStartDate() : trip.getEndDate();
        boolean dateMatches = dateMatches(leg.departureAt(), leg.arrivalAt(), targetDate);
        if (!airportMatches || !dateMatches) {
            return Optional.empty();
        }

        int score = 60 + 30 + statusScore(leg.status());
        String title = join(leg.carrierName(), leg.flightNumber());
        String detail = join(" → ", leg.origin(), leg.destination());
        return Optional.of(new BookingMatchResponse.FlightMatch(
                leg.leg(), title, detail, leg.status().name(), statusLabel(leg.status()),
                leg.bookingRef(), leg.origin(), leg.destination(), leg.departureAt(), leg.arrivalAt(), score));
    }

    private Optional<BookingMatchResponse.FlightMatch> flightMatch(
            FlightBookingDTO flight, TripDTO trip, String destinationAirport) {
        if (flight.getLeg() == null || flight.getOrigin() == null
                || flight.getDestination() == null || destinationAirport == null) {
            return Optional.empty();
        }

        boolean airportMatches = flight.getLeg() == FlightBookingService.OUTBOUND_LEG
                ? destinationAirport.equalsIgnoreCase(flight.getDestination())
                : destinationAirport.equalsIgnoreCase(flight.getOrigin());
        LocalDate targetDate = flight.getLeg() == FlightBookingService.OUTBOUND_LEG
                ? trip.getStartDate() : trip.getEndDate();
        boolean dateMatches = dateMatches(flight.getDepartureAt(), flight.getArrivalAt(), targetDate);
        if (!airportMatches || !dateMatches) {
            return Optional.empty();
        }

        BookingStatus status = flight.status();
        int score = 60 + 30 + statusScore(status);
        return Optional.of(new BookingMatchResponse.FlightMatch(
                flight.getLeg(), join(" ", flight.getCarrierName(), flight.getFlightNumber()),
                join(" → ", flight.getOrigin(), flight.getDestination()), status.name(), statusLabel(status),
                flight.getBookingRef(), flight.getOrigin(), flight.getDestination(),
                flight.getDepartureAt(), flight.getArrivalAt(), score));
    }

    private Optional<BookingMatchResponse.AccommodationMatch> accommodationMatch(
            TripAccommodationsResponse.Stay stay, TripDTO trip) {
        if (!dateRangeMatches(stay.checkIn(), stay.checkOut(), trip.getStartDate(), trip.getEndDate())) {
            return Optional.empty();
        }
        String searchable = normalize(join(" ", stay.name(), stay.areaLabel(), stay.address()));
        String destination = normalize(trip.getDestinationName());
        String baseDestination = destination.replaceAll("(특별시|광역시|특별자치시|특별자치도|도|시|군|구)$", "");
        if (destination.isBlank() || (!searchable.contains(destination) && !searchable.contains(baseDestination))) {
            return Optional.empty();
        }

        int score = 60 + 30 + accommodationStatusScore(stay.status().name());
        return Optional.of(new BookingMatchResponse.AccommodationMatch(
                stay.accommodationBookingId(), stay.name(), stay.areaLabel(), stay.status().name(),
                accommodationStatusLabel(stay.status().name()), stay.bookingRef(), stay.areaLabel(), stay.address(),
                stay.checkIn(), stay.checkOut(), score));
    }

    private Optional<BookingMatchResponse.AccommodationMatch> accommodationMatch(
            AccommodationBookingDTO stay, TripDTO trip) {
        if (!dateRangeMatches(stay.getCheckIn(), stay.getCheckOut(),
                trip.getStartDate(), trip.getEndDate())) {
            return Optional.empty();
        }
        String searchable = normalize(join(" ", stay.getName(), stay.getAreaLabel(), stay.getAddress()));
        String destination = normalize(trip.getDestinationName());
        String baseDestination = destination.replaceAll(
                "(특별시|광역시|특별자치시|특별자치도|도|시|군|구)$", "");
        if (destination.isBlank()
                || (!searchable.contains(destination) && !searchable.contains(baseDestination))) {
            return Optional.empty();
        }

        String status = stay.status().name();
        int score = 60 + 30 + accommodationStatusScore(status);
        return Optional.of(new BookingMatchResponse.AccommodationMatch(
                stay.getAccommodationBookingId(), stay.getName(), stay.getAreaLabel(), status,
                accommodationStatusLabel(status), stay.getBookingRef(), stay.getAreaLabel(), stay.getAddress(),
                stay.getCheckIn().toString(), stay.getCheckOut().toString(), score));
    }

    private boolean dateMatches(OffsetDateTime departureAt, OffsetDateTime arrivalAt, LocalDate target) {
        return target != null && ((departureAt != null && target.equals(departureAt.toLocalDate()))
                || (arrivalAt != null && target.equals(arrivalAt.toLocalDate())));
    }

    private boolean dateRangeMatches(String checkIn, String checkOut, LocalDate start, LocalDate end) {
        try {
            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);
            return start != null && end != null && !in.isBefore(start) && !out.isAfter(end);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean dateRangeMatches(LocalDate checkIn, LocalDate checkOut, LocalDate start, LocalDate end) {
        return checkIn != null && checkOut != null && start != null && end != null
                && !checkIn.isBefore(start) && !checkOut.isAfter(end);
    }

    private String airportOf(String destination) {
        String normalized = normalize(destination);
        return DESTINATION_AIRPORTS.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .filter(entry -> normalized.contains(normalize(entry.getKey())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return Objects.toString(value, "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private int statusScore(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> 10;
            case USER_REPORTED -> 8;
            case NONE -> 5;
        };
    }

    private int accommodationStatusScore(String status) {
        return "CONFIRMED".equals(status) ? 10 : "USER_REPORTED".equals(status) ? 8 : 5;
    }

    private String statusLabel(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "확정";
            case USER_REPORTED -> "예약함 (직접 표시)";
            case NONE -> "선택만 함";
        };
    }

    private String accommodationStatusLabel(String status) {
        return switch (status) {
            case "CONFIRMED" -> "확정";
            case "USER_REPORTED" -> "예약함 (직접 표시)";
            default -> "선택 완료";
        };
    }

    private String join(String separator, String... values) {
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + separator + right)
                .orElse("");
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
