package org.example.all_my_trip_project.domain.trip.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripDayMapper {
    int insert(TripDayDTO tripDay);
    int insertAll(@Param("tripDays") List<TripDayDTO> tripDays);
    Optional<TripDayDTO> findById(Long tripDayId);
    List<TripDayDTO> findByTripId(Long tripId);
    int moveOutOfDateRange(Long tripId);
    int update(TripDayDTO tripDay);
    int delete(Long tripDayId);
}
