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
    private final TripDayDAO tripDayDAO;
    private final TripDAO tripDAO;

    @Transactional
    public Long create(Long userId, TripDayDTO tripDay) {
        requireOwnedTrip(userId, tripDay.getTripId());
        tripDayDAO.insert(tripDay);
        return tripDay.getTripDayId();
    }

    public List<TripDayDTO> getByTrip(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);
        return tripDayDAO.findByTripId(tripId);
    }

    @Transactional
    public void update(Long userId, TripDayDTO tripDay) {
        requireOwnedDay(userId, tripDay.getTripId(), tripDay.getTripDayId());
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

    private void requireOwnedTrip(Long userId, Long tripId) {
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다."));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new IllegalArgumentException("여행을 찾을 수 없습니다.");
        }
    }

    private void requireOwnedDay(Long userId, Long tripId, Long tripDayId) {
        requireOwnedTrip(userId, tripId);
        TripDayDTO savedDay = tripDayDAO.findById(tripDayId)
                .orElseThrow(() -> new IllegalArgumentException("여행 일자를 찾을 수 없습니다."));
        if (!Objects.equals(savedDay.getTripId(), tripId)) {
            throw new IllegalArgumentException("여행 일자를 찾을 수 없습니다.");
        }
    }
}
