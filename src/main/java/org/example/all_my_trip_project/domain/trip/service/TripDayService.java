package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripDayService {
    private static final int MAX_TRIP_DAYS = 30;
    private final TripDayDAO tripDayDAO;
    private final TripDAO tripDAO;

    @Transactional
    public Long create(Long userId, TripDayDTO tripDay) {
        TripDTO trip = requireOwnedTrip(userId, tripDay.getTripId());
        List<TripDayDTO> existingDays = tripDayDAO.findByTripId(tripDay.getTripId());
        if (existingDays.size() >= MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("여행 일자는 최대 30개까지 등록할 수 있습니다.");
        }
        validateDay(trip, tripDay, existingDays, null);
        tripDayDAO.insert(tripDay);
        return tripDay.getTripDayId();
    }

    public List<TripDayDTO> getByTrip(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);
        return tripDayDAO.findByTripId(tripId);
    }

    @Transactional
    public void update(Long userId, TripDayDTO tripDay) {
        TripDTO trip = requireOwnedDay(userId, tripDay.getTripId(), tripDay.getTripDayId());
        validateDay(trip, tripDay, tripDayDAO.findByTripId(tripDay.getTripId()), tripDay.getTripDayId());
        if (tripDayDAO.update(tripDay) == 0) {
            throw new IllegalArgumentException("수정할 여행 일자를 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long userId, Long tripId, Long tripDayId) {
        requireOwnedDay(userId, tripId, tripDayId);
        if (tripDayDAO.delete(tripDayId) == 0) {
            throw new IllegalArgumentException("삭제할 여행 일자를 찾을 수 없습니다.");
        }
    }

    private TripDTO requireOwnedTrip(Long userId, Long tripId) {
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다."));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new IllegalArgumentException("여행을 찾을 수 없습니다.");
        }
        return trip;
    }

    private TripDTO requireOwnedDay(Long userId, Long tripId, Long tripDayId) {
        TripDTO trip = requireOwnedTrip(userId, tripId);
        TripDayDTO savedDay = tripDayDAO.findById(tripDayId)
                .orElseThrow(() -> new IllegalArgumentException("여행 일자를 찾을 수 없습니다."));
        if (!Objects.equals(savedDay.getTripId(), tripId)) {
            throw new IllegalArgumentException("여행 일자를 찾을 수 없습니다.");
        }
        return trip;
    }

    private void validateDay(TripDTO trip, TripDayDTO candidate,
                             List<TripDayDTO> existingDays, Long excludedDayId) {
        if (candidate.getDayNumber() == null || candidate.getTripDate() == null) {
            throw new IllegalArgumentException("dayNumber와 tripDate는 필수입니다.");
        }
        long tripLength = java.time.temporal.ChronoUnit.DAYS
                .between(trip.getStartDate(), trip.getEndDate()) + 1;
        if (candidate.getDayNumber() < 1 || candidate.getDayNumber() > tripLength
                || candidate.getDayNumber() > MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("dayNumber는 여행 기간 안의 일차여야 합니다.");
        }
        if (candidate.getTripDate().isBefore(trip.getStartDate())
                || candidate.getTripDate().isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException("tripDate는 여행 시작일과 종료일 사이여야 합니다.");
        }
        boolean duplicated = existingDays.stream()
                .filter(day -> !Objects.equals(day.getTripDayId(), excludedDayId))
                .anyMatch(day -> Objects.equals(day.getDayNumber(), candidate.getDayNumber())
                        || Objects.equals(day.getTripDate(), candidate.getTripDate()));
        if (duplicated) {
            throw new IllegalArgumentException("dayNumber와 tripDate는 여행 안에서 중복될 수 없습니다.");
        }
    }
}
