package org.example.all_my_trip_project.domain.accommodation.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingRefRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOutboundClickDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.RecordAccommodationClickRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.ReportAccommodationBookedRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.SaveAccommodationRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.mapper.AccommodationBookingMapper;
import org.example.all_my_trip_project.domain.accommodation.mapper.AccommodationOutboundClickMapper;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AccommodationBookingService {

    private static final String OUTCOME_REPORTED_YES = "REPORTED_YES";
    private static final String OUTCOME_LATER = "LATER";

    private final AccommodationBookingMapper accommodationBookingMapper;
    private final AccommodationOutboundClickMapper accommodationOutboundClickMapper;
    private final TripDAO tripDAO;

    /**
     * 숙소 선택을 여행에 저장한다.
     *
     * <p>같은 기간을 다시 고르면 새 행을 만들지 않고 스냅샷을 갈아끼운다(매퍼의 upsert).
     * 기간이 겹치는 다른 숙소가 이미 있으면 거절한다 — 한 밤에 두 숙소는 실수다.
     */
    @Transactional
    public TripAccommodationsResponse save(Long userId, Long tripId, SaveAccommodationRequest request) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        validatePeriod(trip, request);
        rejectOverlap(tripId, request);

        accommodationBookingMapper.upsertSelection(toDto(userId, tripId, request));
        return getBookings(userId, tripId);
    }

    /**
     * 담아둔 숙소를 뺀다.
     *
     * <p>이 숙소로 나갔던 이탈 이력도 함께 사라진다(FK CASCADE). 없어진 숙소를 두고
     * "예약하셨나요?"라고 물을 수는 없다.
     */
    @Transactional
    public TripAccommodationsResponse remove(Long userId, Long tripId, Long accommodationBookingId) {
        requireOwnedTrip(userId, tripId);
        requireBookingInTrip(tripId, accommodationBookingId);

        accommodationBookingMapper.delete(accommodationBookingId);
        return getBookings(userId, tripId);
    }

    /**
     * 딥링크 클릭 기록.
     *
     * <p>항공은 나가는 순간에 선택을 함께 저장했다. 복귀 감지를 놓치면 선택이 통째로
     * 사라지기 때문이었다. 숙박은 카드에서 고를 때 이미 저장되므로 여기서는 이탈만 남긴다.
     *
     * @return 이 이탈 건의 id. 복귀 후 자가 신고가 이 값으로 결과를 적는다
     */
    @Transactional
    public Long recordOutboundClick(Long userId, Long tripId, Long accommodationBookingId,
                                    RecordAccommodationClickRequest request) {
        requireOwnedTrip(userId, tripId);
        AccommodationBookingDTO booking = requireBookingInTrip(tripId, accommodationBookingId);

        AccommodationOutboundClickDTO click = AccommodationOutboundClickDTO.builder()
                .accommodationBookingId(accommodationBookingId)
                .userId(userId)
                .tripId(tripId)
                .offerId(booking.getOfferId())
                .provider(booking.getProvider())
                .deeplinkUrl(Objects.requireNonNullElse(request.deeplinkUrl(), ""))
                .build();
        accommodationOutboundClickMapper.insert(click);

        return click.getAccommodationOutboundClickId();
    }

    /**
     * 자가 신고.
     *
     * <p>{@code userReportedBooked=false}는 "아니요"가 아니라 "나중에 확인할게요"다.
     * 담아둔 숙소는 그대로 두고 예약 표시만 하지 않는다. 선택을 되돌리는 것은 {@link #remove}다.
     */
    @Transactional
    public TripAccommodationsResponse reportBooked(Long userId, Long tripId, Long accommodationBookingId,
                                                   ReportAccommodationBookedRequest request) {
        requireOwnedTrip(userId, tripId);
        requireBookingInTrip(tripId, accommodationBookingId);

        boolean reported = Boolean.TRUE.equals(request.userReportedBooked());
        accommodationBookingMapper.updateUserReported(accommodationBookingId, reported);
        resolveClick(request.clickId(), reported ? OUTCOME_REPORTED_YES : OUTCOME_LATER);

        return getBookings(userId, tripId);
    }

    /** 예약번호를 넣으면 확정으로 승격하고, 지우면 자가 신고 상태로 되돌아간다. */
    @Transactional
    public TripAccommodationsResponse updateBookingRef(Long userId, Long tripId, Long accommodationBookingId,
                                                       AccommodationBookingRefRequest request) {
        requireOwnedTrip(userId, tripId);
        requireBookingInTrip(tripId, accommodationBookingId);

        String bookingRef = request.bookingRef() == null
                ? null
                : request.bookingRef().trim().toUpperCase();
        accommodationBookingMapper.updateBookingRef(accommodationBookingId, bookingRef);

        return getBookings(userId, tripId);
    }

    @Transactional(readOnly = true)
    public TripAccommodationsResponse getBookings(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);
        return TripAccommodationsResponse.from(
                accommodationBookingMapper.findByTrip(tripId),
                accommodationOutboundClickMapper.findUnresolvedByTrip(tripId));
    }

    /** 복귀 감지를 놓쳐 clickId가 없는 경로도 있으므로 없으면 조용히 넘어간다. */
    private void resolveClick(Long clickId, String outcome) {
        if (clickId != null) {
            accommodationOutboundClickMapper.updateOutcome(clickId, outcome);
        }
    }

    /** 남의 여행에 달린 예약을 id만 알아내 건드리지 못하게 한다. */
    private AccommodationBookingDTO requireBookingInTrip(Long tripId, Long accommodationBookingId) {
        AccommodationBookingDTO booking = accommodationBookingMapper.findById(accommodationBookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOMMODATION_BOOKING_NOT_FOUND));
        if (!Objects.equals(booking.getTripId(), tripId)) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_BOOKING_NOT_FOUND);
        }
        return booking;
    }

    /**
     * 여행 기간을 벗어난 숙소는 받지 않는다.
     *
     * <p>화면이 여행 날짜로 검색 조건을 채우므로 정상 흐름에서는 벗어날 일이 없지만,
     * API를 직접 호출하면 8월 여행에 12월 숙소를 넣을 수 있다. 그러면 우측 예약 현황의
     * 합계가 여행과 무관한 금액을 더하게 된다.
     */
    private void validatePeriod(TripDTO trip, SaveAccommodationRequest request) {
        if (!request.checkOut().isAfter(request.checkIn())) {
            throw new BusinessException(ErrorCode.INVALID_ACCOMMODATION_PERIOD);
        }
        if (request.checkIn().isBefore(trip.getStartDate()) || request.checkOut().isAfter(trip.getEndDate())) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_PERIOD_OUT_OF_TRIP);
        }
    }

    /**
     * 기간이 겹치는 숙소가 이미 있으면 거절한다.
     *
     * <p>DB에 EXCLUDE 제약을 걸지 않았으므로 여기서 막는다. 같은 기간은 UNIQUE 인덱스가
     * 잡아내지만 "8/10~8/12"와 "8/11~8/13"처럼 부분적으로 겹치는 경우는 못 잡는다.
     * 같은 기간을 다시 고르는 것은 교체이므로 겹침으로 보지 않는다.
     */
    private void rejectOverlap(Long tripId, SaveAccommodationRequest request) {
        boolean overlaps = accommodationBookingMapper
                .findOverlapping(tripId, request.checkIn(), request.checkOut())
                .stream()
                .anyMatch(existing -> !(existing.getCheckIn().equals(request.checkIn())
                        && existing.getCheckOut().equals(request.checkOut())));
        if (overlaps) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_PERIOD_OVERLAP);
        }
    }

    private AccommodationBookingDTO toDto(Long userId, Long tripId, SaveAccommodationRequest request) {
        AccommodationBookingDTO dto = new AccommodationBookingDTO();
        dto.setTripId(tripId);
        dto.setUserId(userId);
        dto.setCheckIn(request.checkIn());
        dto.setCheckOut(request.checkOut());
        dto.setOfferId(request.offerId());
        dto.setProvider(request.provider());
        dto.setName(request.name());
        dto.setAccommodationType(request.accommodationType());
        dto.setAreaLabel(request.areaLabel());
        dto.setAddress(request.address());
        dto.setRating(request.rating());
        dto.setLatitude(request.latitude());
        dto.setLongitude(request.longitude());
        dto.setQuotedNightlyPrice(request.nightlyPrice());
        dto.setQuotedTotalPrice(request.totalPrice());
        dto.setQuotedCurrency(request.currency());
        dto.setQuotedPriceSource(request.priceSource());
        dto.setRooms(request.rooms());
        dto.setAdults(request.adults());
        return dto;
    }

    /** 남의 여행은 존재 자체를 알리지 않는다. TripService·FlightBookingService와 같은 규칙이다. */
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
