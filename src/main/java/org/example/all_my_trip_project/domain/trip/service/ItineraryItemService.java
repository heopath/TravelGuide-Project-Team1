package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryItemService {
    private final ItineraryItemDAO itemDAO;

    @Transactional
    public Long create(ItineraryItemDTO item) {
        itemDAO.insert(item);
        return item.getItineraryItemId();
    }

    public List<ItineraryItemDTO> getByTripDay(Long tripDayId) {
        return itemDAO.findByTripDayId(tripDayId);
    }

    @Transactional
    public void update(ItineraryItemDTO item) {
        if (itemDAO.update(item) == 0) {
            throw new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long itemId) {
        if (itemDAO.delete(itemId) == 0) {
            throw new IllegalArgumentException("삭제할 일정 항목을 찾을 수 없습니다.");
        }
    }
}
