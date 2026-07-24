package org.example.all_my_trip_project.domain.trip.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripMapper {
    int insert(TripDTO trip);
    Optional<TripDTO> findById(Long tripId);
    List<TripDTO> findByUserId(Long userId);
    int update(TripDTO trip);
    int softDelete(Long tripId);
}
