package org.example.all_my_trip_project.domain.trip.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ItineraryItemMapper {
    int insert(ItineraryItemDTO item);
    Optional<ItineraryItemDTO> findById(Long itineraryItemId);
    List<ItineraryItemDTO> findByTripDayId(Long tripDayId);
    int update(ItineraryItemDTO item);
    int delete(Long itineraryItemId);
}
