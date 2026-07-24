package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripDayService {
    private final TripDayDAO tripDayDAO;

    @Transactional
    public Long create(TripDayDTO tripDay) {
        tripDayDAO.insert(tripDay);
        return tripDay.getTripDayId();
    }

    public List<TripDayDTO> getByTrip(Long tripId) {
        return tripDayDAO.findByTripId(tripId);
    }

    @Transactional
    public void update(TripDayDTO tripDay) {
        if (tripDayDAO.update(tripDay) == 0) {
            throw new IllegalArgumentException("수정할 여행 일자를 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long tripDayId) {
        if (tripDayDAO.delete(tripDayId) == 0) {
            throw new IllegalArgumentException("삭제할 여행 일자를 찾을 수 없습니다.");
        }
    }
}
