package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryItemService {
    private final ItineraryItemDAO itemDAO;
    private final TripDayDAO tripDayDAO;
    private final TripDAO tripDAO;

    @Transactional
    public Long create(Long userId, ItineraryItemDTO item) {
        requireOwnedTripDay(userId, item.getTripDayId());
        itemDAO.insert(item);
        return item.getItineraryItemId();
    }

    public List<ItineraryItemDTO> getByTripDay(Long userId, Long tripDayId) {
        requireOwnedTripDay(userId, tripDayId);
        return itemDAO.findByTripDayId(tripDayId);
    }

    @Transactional
    public void update(Long userId, ItineraryItemDTO item) {
        requireOwnedItem(userId, item.getTripDayId(), item.getItineraryItemId());
        if (itemDAO.update(item) == 0) {
            throw new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long userId, Long tripDayId, Long itemId) {
        requireOwnedItem(userId, tripDayId, itemId);
        if (itemDAO.delete(itemId) == 0) {
            throw new IllegalArgumentException("삭제할 일정 항목을 찾을 수 없습니다.");
        }
    }

    private void requireOwnedTripDay(Long userId, Long tripDayId) {
        TripDayDTO day = tripDayDAO.findById(tripDayId)
                .orElseThrow(() -> new IllegalArgumentException("여행 일자를 찾을 수 없습니다."));
        TripDTO trip = tripDAO.findById(day.getTripId())
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다."));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new IllegalArgumentException("여행 일자를 찾을 수 없습니다.");
        }
    }

    private void requireOwnedItem(Long userId, Long tripDayId, Long itemId) {
        requireOwnedTripDay(userId, tripDayId);
        ItineraryItemDTO savedItem = itemDAO.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("일정 항목을 찾을 수 없습니다."));
        if (!Objects.equals(savedItem.getTripDayId(), tripDayId)) {
            throw new IllegalArgumentException("일정 항목을 찾을 수 없습니다.");
        }
    }
}
