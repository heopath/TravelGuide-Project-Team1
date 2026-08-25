package org.example.all_my_trip_project.domain.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.SaveAccommodationRequest;
import org.example.all_my_trip_project.domain.accommodation.mapper.AccommodationBookingMapper;
import org.example.all_my_trip_project.domain.booking.dto.BookingBatchResponse;
import org.example.all_my_trip_project.domain.booking.dto.StandaloneBookingConfirmRequest;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickRequest;
import org.example.all_my_trip_project.domain.flight.mapper.FlightBookingMapper;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 여행 생성 전 예약 확정과, 완성된 여행으로의 후속 연결을 담당한다. */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class StandaloneBookingService {

    private final FlightBookingMapper flightBookingMapper;
    private final AccommodationBookingMapper accommodationBookingMapper;
    private final TripDAO tripDAO;

    @Transactional
    public BookingBatchResponse confirm(Long userId, UUID bookingBatchId,
                                        StandaloneBookingConfirmRequest request) {
        requireUser(userId);
        if (!request.accommodation().checkOut().isAfter(request.accommodation().checkIn())) {
            throw new BusinessException(ErrorCode.INVALID_ACCOMMODATION_PERIOD);
        }

        for (int leg = 0; leg < request.flights().size(); leg++) {
            flightBookingMapper.upsertStandaloneSelection(
                    flight(userId, bookingBatchId, leg, request.flights().get(leg)));
        }
        accommodationBookingMapper.upsertStandaloneSelection(
                accommodation(userId, bookingBatchId, request.accommodation()));

        return response(userId, bookingBatchId, null);
    }

    @Transactional
    public BookingBatchResponse link(Long userId, UUID bookingBatchId, Long tripId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        List<FlightBookingDTO> flights = flightBookingMapper.findByUserAndBatch(userId, bookingBatchId);
        List<AccommodationBookingDTO> stays =
                accommodationBookingMapper.findByUserAndBatch(userId, bookingBatchId);
        if (flights.size() != 2 || stays.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOKING_BATCH_NOT_FOUND);
        }

        Long linkedTripId = linkedTripId(flights, stays);
        if (linkedTripId != null) {
            if (!Objects.equals(linkedTripId, tripId)) {
                throw new BusinessException(ErrorCode.BOOKING_BATCH_ALREADY_LINKED);
            }
            boolean partiallyLinked = flights.stream().anyMatch(flight -> flight.getTripId() == null)
                    || stays.stream().anyMatch(stay -> stay.getTripId() == null);
            if (partiallyLinked) {
                throw new BusinessException(ErrorCode.BOOKING_BATCH_ALREADY_LINKED);
            }
            return response(userId, bookingBatchId, linkedTripId);
        }

        validateTripPeriod(trip, flights, stays);
        if (!flightBookingMapper.findByTrip(tripId).isEmpty()
                || !accommodationBookingMapper.findByTrip(tripId).isEmpty()) {
            throw new BusinessException(ErrorCode.BOOKING_BATCH_TRIP_CONFLICT);
        }

        if (flightBookingMapper.linkBatchToTrip(userId, bookingBatchId, tripId) != 2
                || accommodationBookingMapper.linkBatchToTrip(userId, bookingBatchId, tripId) != stays.size()) {
            throw new BusinessException(ErrorCode.BOOKING_BATCH_NOT_FOUND);
        }
        if (tripDAO.confirmBooking(tripId) != 1) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return response(userId, bookingBatchId, tripId);
    }

    private BookingBatchResponse response(Long userId, UUID batchId, Long tripId) {
        int flights = flightBookingMapper.findByUserAndBatch(userId, batchId).size();
        int stays = accommodationBookingMapper.findByUserAndBatch(userId, batchId).size();
        return new BookingBatchResponse(batchId, tripId, flights, stays, tripId != null);
    }

    private Long linkedTripId(List<FlightBookingDTO> flights, List<AccommodationBookingDTO> stays) {
        Long result = null;
        for (Long tripId : java.util.stream.Stream.concat(
                flights.stream().map(FlightBookingDTO::getTripId),
                stays.stream().map(AccommodationBookingDTO::getTripId)).filter(Objects::nonNull).toList()) {
            if (result != null && !Objects.equals(result, tripId)) {
                throw new BusinessException(ErrorCode.BOOKING_BATCH_ALREADY_LINKED);
            }
            result = tripId;
        }
        return result;
    }

    private void validateTripPeriod(TripDTO trip, List<FlightBookingDTO> flights,
                                    List<AccommodationBookingDTO> stays) {
        boolean flightOutside = flights.stream().anyMatch(flight ->
                flight.getDepartureAt() == null
                        || flight.getDepartureAt().toLocalDate().isBefore(trip.getStartDate())
                        || flight.getDepartureAt().toLocalDate().isAfter(trip.getEndDate()));
        boolean stayOutside = stays.stream().anyMatch(stay ->
                stay.getCheckIn().isBefore(trip.getStartDate())
                        || stay.getCheckOut().isAfter(trip.getEndDate()));
        if (flightOutside || stayOutside) {
            throw new BusinessException(ErrorCode.BOOKING_BATCH_PERIOD_MISMATCH);
        }
    }

    private FlightBookingDTO flight(Long userId, UUID batchId, int leg, OutboundClickRequest request) {
        return FlightBookingDTO.builder()
                .bookingBatchId(batchId).userId(userId).leg(leg)
                .offerId(request.offerId()).provider(request.provider())
                .origin(request.origin()).destination(request.destination())
                .carrierCode(request.carrierCode()).carrierName(request.carrierName())
                .flightNumber(request.flightNumber()).departureAt(request.departureAt())
                .arrivalAt(request.arrivalAt()).quotedTotalPrice(request.totalPrice())
                .quotedCurrency(request.currency()).quotedPriceSource(request.priceSource().name())
                .userReportedBooked(true)
                .build();
    }

    private AccommodationBookingDTO accommodation(Long userId, UUID batchId,
                                                   SaveAccommodationRequest request) {
        AccommodationBookingDTO dto = new AccommodationBookingDTO();
        dto.setBookingBatchId(batchId);
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
        dto.setUserReportedBooked(true);
        return dto;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private TripDTO requireOwnedTrip(Long userId, Long tripId) {
        requireUser(userId);
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }
}
