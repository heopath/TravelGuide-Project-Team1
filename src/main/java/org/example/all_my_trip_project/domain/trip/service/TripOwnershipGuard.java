package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 여행·일차·일정 세 단계의 소유권 검사를 한 곳에서 처리한다.
 * 존재하지 않거나 다른 사용자가 소유한 리소스는 모두 {@link ErrorCode#TRIP_NOT_FOUND}(404)로 통일한다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
class TripOwnershipGuard {
    private final TripDAO tripDAO;
    private final TripDayDAO tripDayDAO;
    private final ItineraryItemDAO itemDAO;

    TripDTO requireOwnedTrip(Long userId, Long tripId) {
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

    TripDTO requireOwnedDay(Long userId, Long tripId, Long tripDayId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        TripDayDTO day = tripDayDAO.findById(tripDayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(day.getTripId(), tripId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    TripDTO requireOwnedTripDay(Long userId, Long tripDayId) {
        TripDayDTO day = tripDayDAO.findById(tripDayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        return requireOwnedTrip(userId, day.getTripId());
    }

    TripDTO requireOwnedItem(Long userId, Long tripDayId, Long itemId) {
        TripDTO trip = requireOwnedTripDay(userId, tripDayId);
        ItineraryItemDTO item = itemDAO.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(item.getTripDayId(), tripDayId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }
}
