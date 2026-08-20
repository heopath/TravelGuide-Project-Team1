package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryTimeBatchUpdateRequest;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!ui")
@Transactional(readOnly = true)
@RequiredArgsConstructor
class ItineraryItemService {
    private final ItineraryItemDAO itemDAO;
    private final TripOwnershipGuard ownershipGuard;
    private final ItineraryItemValidator itineraryItemValidator;
    private final ItineraryItemTimeConflictValidator timeConflictValidator;

    @Transactional
    public Long create(Long userId, ItineraryItemDTO item) {
        ownershipGuard.requireOwnedTripDay(userId, item.getTripDayId());
        // 생성 API에서 전달한 ID는 신뢰하지 않는다. MyBatis가 INSERT 후 생성된 키만 설정한다.
        item.setItineraryItemId(null);
        itineraryItemValidator.validate(item);
        int existingCount = itemDAO.countByTripDayId(item.getTripDayId());
        if (existingCount >= TripPolicy.MAX_ITINERARY_ITEMS_PER_DAY) {
            throw new BusinessException(ErrorCode.ITINERARY_ITEM_LIMIT_EXCEEDED);
        }
        if (item.getPlaceId() != null
                && itemDAO.existsByTripDayIdAndPlaceId(item.getTripDayId(), item.getPlaceId())) {
            throw new BusinessException(ErrorCode.ITINERARY_PLACE_ALREADY_ADDED);
        }
        if ("AI".equalsIgnoreCase(item.getSource())) {
            if (timeConflictValidator.exceedsAiDayBoundary(item)
                    || timeConflictValidator.hasConflict(item, itemDAO.findByTripDayId(item.getTripDayId()))) {
                throw new BusinessException(ErrorCode.ITINERARY_TIME_CONFLICT);
            }
        }
        // 삭제로 중간 순번이 비어도 마지막 순번 뒤에 추가한다.
        item.setSortOrder(itemDAO.nextSortOrderByTripDayId(item.getTripDayId()));
        try {
            itemDAO.insert(item);
        } catch (DataIntegrityViolationException exception) {
            // 동시 요청에서 DB의 (trip_day_id, place_id) 유일 인덱스가 중복을 막는다.
            if (item.getPlaceId() != null
                    && itemDAO.existsByTripDayIdAndPlaceId(item.getTripDayId(), item.getPlaceId())) {
                throw new BusinessException(ErrorCode.ITINERARY_PLACE_ALREADY_ADDED);
            }
            throw exception;
        }
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
        if (timeConflictValidator.hasConflictExcludingSameItem(
                item, itemDAO.findByTripDayId(item.getTripDayId()))) {
            throw new BusinessException(ErrorCode.ITINERARY_TIME_CONFLICT);
        }
        if (itemDAO.update(item) == 0) {
            throw new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public List<ItineraryItemDTO> updateScheduleTimes(
            Long userId,
            Long tripDayId,
            ItineraryTimeBatchUpdateRequest request) {
        ownershipGuard.requireOwnedTripDay(userId, tripDayId);
        List<ItineraryItemDTO> existingItems = itemDAO.findByTripDayId(tripDayId);
        List<ItineraryTimeBatchUpdateRequest.ItemTime> requestedTimes = request.items();

        Set<Long> existingIds = existingItems.stream()
                .map(ItineraryItemDTO::getItineraryItemId)
                .collect(Collectors.toSet());
        Set<Long> requestedIds = requestedTimes.stream()
                .map(ItineraryTimeBatchUpdateRequest.ItemTime::itineraryItemId)
                .collect(Collectors.toSet());
        if (existingItems.size() != requestedTimes.size()
                || requestedIds.size() != requestedTimes.size()
                || !existingIds.equals(requestedIds)) {
            throw new IllegalArgumentException("해당 일자의 모든 일정 시간을 보내야 합니다.");
        }

        Map<Long, ItineraryTimeBatchUpdateRequest.ItemTime> timesByItemId = requestedTimes.stream()
                .collect(Collectors.toMap(
                        ItineraryTimeBatchUpdateRequest.ItemTime::itineraryItemId,
                        Function.identity()));
        for (ItineraryItemDTO item : existingItems) {
            ItineraryTimeBatchUpdateRequest.ItemTime itemTime = timesByItemId.get(item.getItineraryItemId());
            item.setStartTime(itemTime.startTime());
            item.setEndTime(itemTime.endTime());
            itineraryItemValidator.validate(item);
        }
        for (ItineraryItemDTO item : existingItems) {
            if (itemDAO.update(item) == 0) {
                throw new IllegalArgumentException("수정할 일정 항목을 찾을 수 없습니다.");
            }
        }
        return existingItems;
    }

    @Transactional
    public void reorder(Long userId, Long tripDayId, List<Long> itemIds) {
        ownershipGuard.requireOwnedTripDay(userId, tripDayId);
        List<ItineraryItemDTO> existing = itemDAO.findByTripDayId(tripDayId);
        if (existing.size() != itemIds.size()) {
            throw new IllegalArgumentException("일정 항목 전체 순서를 보내야 합니다.");
        }
        java.util.Set<Long> existingIds = existing.stream()
                .map(ItineraryItemDTO::getItineraryItemId)
                .collect(java.util.stream.Collectors.toSet());
        if (itemIds.stream().anyMatch(id -> id == null || !existingIds.contains(id))
                || itemIds.size() != new java.util.HashSet<>(itemIds).size()) {
            throw new IllegalArgumentException("다른 일차의 일정 항목은 순서를 변경할 수 없습니다.");
        }
        // (trip_day_id, sort_order)가 유니크이므로 기존 순번과 바로 교환하면 충돌한다.
        // 먼저 일정 범위를 벗어난 임시 순번으로 이동한 뒤 최종 순번을 저장한다.
        int temporaryBase = 1000;
        for (int index = 0; index < itemIds.size(); index++) {
            itemDAO.updateSortOrder(itemIds.get(index), temporaryBase + index);
        }
        for (int index = 0; index < itemIds.size(); index++) {
            itemDAO.updateSortOrder(itemIds.get(index), index);
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
