package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {
    private final TripDAO tripDAO;

    @Transactional
    public Long create(TripDTO trip) {
        validateDates(trip);
        tripDAO.insert(trip);
        return trip.getTripId();
    }

    public TripDTO get(Long tripId) {
        return tripDAO.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다. tripId=" + tripId));
    }

    public List<TripDTO> getByUser(Long userId) {
        return tripDAO.findByUserId(userId);
    }

    @Transactional
    public void update(TripDTO trip) {
        validateDates(trip);
        if (tripDAO.update(trip) == 0) {
            throw new IllegalArgumentException("수정할 여행을 찾을 수 없습니다. tripId=" + trip.getTripId());
        }
    }

    @Transactional
    public void delete(Long tripId) {
        if (tripDAO.softDelete(tripId) == 0) {
            throw new IllegalArgumentException("삭제할 여행을 찾을 수 없습니다. tripId=" + tripId);
        }
    }

    private void validateDates(TripDTO trip) {
        if (trip.getStartDate() != null && trip.getEndDate() != null
                && trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}
