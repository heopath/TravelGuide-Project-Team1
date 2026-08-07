package org.example.all_my_trip_project.domain.trip.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.mapper.ItineraryItemMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class ItineraryItemDAO {
    private final ItineraryItemMapper itemMapper;

    public int insert(ItineraryItemDTO item) { return itemMapper.insert(item); }
    public int update(ItineraryItemDTO item) { return itemMapper.update(item); }
    public int delete(Long itemId) { return itemMapper.delete(itemId); }
    public Optional<ItineraryItemDTO> findById(Long itemId) { return itemMapper.findById(itemId); }
    public List<ItineraryItemDTO> findByTripDayId(Long tripDayId) { return itemMapper.findByTripDayId(tripDayId); }
    public int countByTripDayId(Long tripDayId) { return itemMapper.countByTripDayId(tripDayId); }
}
