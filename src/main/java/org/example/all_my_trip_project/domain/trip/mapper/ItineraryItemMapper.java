package org.example.all_my_trip_project.domain.trip.mapper;

import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Mapper;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ItineraryItemMapper {
    int insert(ItineraryItemDTO item);
    Optional<ItineraryItemDTO> findById(Long itineraryItemId);
    List<ItineraryItemDTO> findByTripDayId(Long tripDayId);
    int countByTripDayId(Long tripDayId);
    int nextSortOrderByTripDayId(Long tripDayId);
    boolean existsByTripDayIdAndPlaceId(Long tripDayId, Long placeId);
    int update(ItineraryItemDTO item);
    int updateSortOrder(@Param("itineraryItemId") Long itineraryItemId, @Param("sortOrder") Integer sortOrder);
    int delete(Long itineraryItemId);
}
