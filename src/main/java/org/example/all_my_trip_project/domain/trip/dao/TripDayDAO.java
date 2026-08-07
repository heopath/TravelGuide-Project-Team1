package org.example.all_my_trip_project.domain.trip.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.mapper.TripDayMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class TripDayDAO {
    private final TripDayMapper tripDayMapper;

    public int insert(TripDayDTO tripDay) { return tripDayMapper.insert(tripDay); }
    public int insertAll(List<TripDayDTO> tripDays) { return tripDayMapper.insertAll(tripDays); }
    public Optional<TripDayDTO> findById(Long tripDayId) { return tripDayMapper.findById(tripDayId); }
    public List<TripDayDTO> findByTripId(Long tripId) { return tripDayMapper.findByTripId(tripId); }
    public int update(TripDayDTO tripDay) { return tripDayMapper.update(tripDay); }
    public int delete(Long tripDayId) { return tripDayMapper.delete(tripDayId); }
    public boolean existsOutsidePeriodWithItineraryItems(Long tripId, LocalDate startDate, LocalDate endDate) { return tripDayMapper.existsOutsidePeriodWithItineraryItems(tripId, startDate, endDate); }
}
