package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@Transactional(readOnly = true)
@RequiredArgsConstructor
class ItineraryItemService {
    private final ItineraryItemDAO itemDAO;
    private final TripOwnershipGuard ownershipGuard;
    private final ItineraryItemValidator itineraryItemValidator;

    @Transactional
    public Long create(Long userId, ItineraryItemDTO item) {
        ownershipGuard.requireOwnedTripDay(userId, item.getTripDayId());
        itineraryItemValidator.validate(item);
        int existingCount = itemDAO.countByTripDayId(item.getTripDayId());
        if (existingCount >= TripPolicy.MAX_ITINERARY_ITEMS_PER_DAY) {
            throw new BusinessException(ErrorCode.ITINERARY_ITEM_LIMIT_EXCEEDED);
        }
        // 순서는 Service가 계산한다: 새 항목은 항상 해당 일차의 맨 뒤(0부터 시작하는 다음 인덱스)에 추가된다.
        item.setSortOrder(existingCount);
        itemDAO.insert(item);
        return item.getItineraryItemId();
    }

    public List<ItineraryItemDTO> getByTripDay(Long userId, Long tripDayId) {
        ownershipGuard.requireOwnedTripDay(userId, tripDayId);
        return itemDAO.findByTripDayId(tripDayId);
    }

    @Transactional
    public void update(Long userId, ItineraryItemDTO item) {
        ownershipGuard.requireOwnedItem(userId, item.getTripDayId(), item.getItineraryItemId());
        ItineraryItemDTO existing = itemDAO.findById(item.getItineraryItemId())
                .orElseThrow(() -> new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다."));
        // 순서 변경은 별도 재정렬 기능(TRIP-06)의 책임이므로 일반 수정에서는 기존 sortOrder를 그대로 유지한다.
        item.setSortOrder(existing.getSortOrder());
        itineraryItemValidator.validate(item);
        if (itemDAO.update(item) == 0) {
            throw new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long userId, Long tripDayId, Long itemId) {
        ownershipGuard.requireOwnedItem(userId, tripDayId, itemId);
        if (itemDAO.delete(itemId) == 0) {
            throw new IllegalArgumentException("삭제할 일정 항목을 찾을 수 없습니다.");
        }
    }

}
