package org.example.all_my_trip_project.domain.flight.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dao.FlightBookingDAO;
import org.example.all_my_trip_project.domain.flight.dto.BookingRefRequest;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingLegResponse;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickDTO;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickRequest;
import org.example.all_my_trip_project.domain.flight.dto.ReportBookedRequest;
import org.example.all_my_trip_project.domain.flight.dto.TripFlightBookingsResponse;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 항공 예약 상태 저장·조회.
 *
 * <p>여기서 다루는 것은 "사용자가 무엇을 골랐고 무엇을 예약했다고 말했는가"뿐이다.
 * 결제 여부는 외부 사이트에서 일어나므로 우리가 알 방법이 없다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class FlightBookingService {

    /** 가는 편 / 오는 편. */
    public static final int OUTBOUND_LEG = 0;
    public static final int INBOUND_LEG = 1;

    /** 항공 · 숙소 · 티켓. 숙소와 티켓은 아직 미구현이라 항공만 카운트된다. */
    private static final int PROGRESS_TOTAL = 3;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String OUTCOME_REPORTED_YES = "REPORTED_YES";
    private static final String OUTCOME_REPORTED_NO = "REPORTED_NO";
    private static final String OUTCOME_LATER = "LATER";

    private final FlightBookingDAO flightBookingDAO;
    private final TripDAO tripDAO;

    /**
     * 딥링크 클릭 기록.
     *
     * <p>선택을 <b>이 시점에</b> 저장하는 게 핵심이다. 복귀 감지는 놓칠 수 있으므로,
     * 나갈 때 저장해두지 않으면 사용자가 돌아오지 않았을 때 선택이 통째로 사라진다.
     */
    @Transactional
    public Long recordOutboundClick(Long userId, Long tripId, int leg, OutboundClickRequest request) {
        requireOwnedTrip(userId, tripId);
        requireValidLeg(leg);

        flightBookingDAO.upsertSelection(FlightBookingDTO.builder()
                .tripId(tripId)
                .userId(userId)
                .leg(leg)
                .offerId(request.offerId())
                .provider(request.provider())
                .carrierCode(request.carrierCode())
                .carrierName(request.carrierName())
                .flightNumber(request.flightNumber())
                .departureAt(request.departureAt())
                .arrivalAt(request.arrivalAt())
                .quotedTotalPrice(request.totalPrice())
                .quotedCurrency(request.currency())
                .quotedPriceSource(request.priceSource().name())
                .build());

        return flightBookingDAO.insertOutboundClick(OutboundClickDTO.builder()
                .userId(userId)
                .tripId(tripId)
                .leg(leg)
                .offerId(request.offerId())
                .provider(request.provider())
                .deeplinkUrl(Objects.requireNonNullElse(request.deeplinkUrl(), ""))
                .build());
    }

    /**
     * 자가 신고.
     *
     * <p>{@code userReportedBooked=false}는 "아니요"가 아니라 "나중에 확인할게요"다.
     * 선택은 그대로 두고 예약 표시만 하지 않는다. 선택 자체를 되돌리는 것은
     * {@link #cancelSelection}이다.
     */
    @Transactional
    public void reportBooked(Long userId, Long tripId, int leg, ReportBookedRequest request) {
        requireOwnedTrip(userId, tripId);
        requireValidLeg(leg);
        requireExistingBooking(tripId, leg);

        boolean reported = Boolean.TRUE.equals(request.userReportedBooked());
        flightBookingDAO.updateUserReported(tripId, leg, reported);
        resolveClick(request.clickId(), reported ? OUTCOME_REPORTED_YES : OUTCOME_LATER);
    }

    /** 예약번호를 넣으면 확정으로 승격하고, 지우면 자가 신고 상태로 되돌아간다. */
    @Transactional
    public void updateBookingRef(Long userId, Long tripId, int leg, BookingRefRequest request) {
        requireOwnedTrip(userId, tripId);
        requireValidLeg(leg);
        requireExistingBooking(tripId, leg);

        String bookingRef = request.bookingRef() == null
                ? null
                : request.bookingRef().trim().toUpperCase();
        flightBookingDAO.updateBookingRef(tripId, leg, bookingRef);
    }

    /** 모달2의 "아니요, 다시 볼게요". 선택·자가 신고·예약번호가 전부 사라진다. */
    @Transactional
    public void cancelSelection(Long userId, Long tripId, int leg, Long clickId) {
        requireOwnedTrip(userId, tripId);
        requireValidLeg(leg);

        flightBookingDAO.delete(tripId, leg);
        resolveClick(clickId, OUTCOME_REPORTED_NO);
    }

    @Transactional(readOnly = true)
    public TripFlightBookingsResponse getBookings(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);

        Map<Integer, FlightBookingDTO> byLeg = flightBookingDAO.findByTrip(tripId).stream()
                .collect(Collectors.toMap(FlightBookingDTO::getLeg, Function.identity()));

        List<FlightBookingLegResponse> legs = List.of(
                toLegResponse(OUTBOUND_LEG, byLeg.get(OUTBOUND_LEG)),
                toLegResponse(INBOUND_LEG, byLeg.get(INBOUND_LEG))
        );

        boolean airDone = isReported(byLeg.get(OUTBOUND_LEG)) && isReported(byLeg.get(INBOUND_LEG));

        BigDecimal selectedTotal = byLeg.values().stream()
                .map(FlightBookingDTO::getQuotedTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TripFlightBookingsResponse.UnresolvedOutboundClick> unresolved =
                flightBookingDAO.findUnresolvedClicks(tripId).stream()
                        .map(click -> new TripFlightBookingsResponse.UnresolvedOutboundClick(
                                click.getFlightOutboundClickId(), click.getLeg(), click.getOfferId()))
                        .toList();

        return new TripFlightBookingsResponse(
                legs,
                selectedTotal,
                !airDone,
                airDone,
                airPriceSource(byLeg.values()),
                new TripFlightBookingsResponse.Progress(airDone ? 1 : 0, PROGRESS_TOTAL),
                unresolved
        );
    }

    private boolean isReported(FlightBookingDTO booking) {
        return booking != null && booking.isUserReportedBooked();
    }

    private FlightBookingLegResponse toLegResponse(int leg, FlightBookingDTO booking) {
        if (booking == null) {
            return FlightBookingLegResponse.empty(leg);
        }
        return new FlightBookingLegResponse(
                leg,
                booking.status(),
                booking.getOfferId(),
                booking.getCarrierCode(),
                booking.getCarrierName(),
                booking.getFlightNumber(),
                booking.getDepartureAt() == null ? null : booking.getDepartureAt().format(TIME),
                booking.getArrivalAt() == null ? null : booking.getArrivalAt().format(TIME),
                booking.getQuotedTotalPrice(),
                booking.getQuotedCurrency(),
                booking.getQuotedPriceSource(),
                booking.getBookingRef()
        );
    }

    /** 두 구간의 출처가 다르면 화면 문구가 달라져야 하므로 MIXED로 알린다. */
    private String airPriceSource(Collection<FlightBookingDTO> bookings) {
        Set<String> sources = bookings.stream()
                .map(FlightBookingDTO::getQuotedPriceSource)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        if (sources.isEmpty()) {
            return null;
        }
        return sources.size() == 1 ? sources.iterator().next() : TripFlightBookingsResponse.MIXED;
    }

    /** 복귀 감지를 놓쳐 clickId가 없는 경로도 있으므로 없으면 조용히 넘어간다. */
    private void resolveClick(Long clickId, String outcome) {
        if (clickId != null) {
            flightBookingDAO.updateOutboundClickOutcome(clickId, outcome);
        }
    }

    private void requireExistingBooking(Long tripId, int leg) {
        Optional<FlightBookingDTO> booking = flightBookingDAO.findByTripAndLeg(tripId, leg);
        if (booking.isEmpty()) {
            throw new BusinessException(ErrorCode.FLIGHT_BOOKING_NOT_FOUND);
        }
    }

    private void requireValidLeg(int leg) {
        if (leg != OUTBOUND_LEG && leg != INBOUND_LEG) {
            throw new BusinessException(ErrorCode.INVALID_FLIGHT_LEG);
        }
    }

    /** 남의 여행은 존재 자체를 알리지 않는다. TripService와 같은 규칙이다. */
    private void requireOwnedTrip(Long userId, Long tripId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
    }
}
