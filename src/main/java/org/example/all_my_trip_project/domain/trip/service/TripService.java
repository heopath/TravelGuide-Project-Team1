package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryTimeBatchUpdateRequest;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {
    private final TripDAO tripDAO;
    private final TripDayDAO tripDayDAO;
    private final ActiveMemberGuard activeMemberGuard;
    private final TripOwnershipGuard guard;
    private final TripDayService tripDayService;
    private final ItineraryItemService itineraryItemService;
    private final TripCreateValidator tripCreateValidator;
    private final TripCreationFactory tripCreationFactory;

    @Transactional
    public TripCreateResult create(Long userId, TripCreateRequest request) {
        validateUserId(userId);
        activeMemberGuard.requireActiveMember(userId);
        int dayCount = tripCreateValidator.validate(request);
        TripDTO trip = tripCreationFactory.createTrip(userId, request);

        if (tripDAO.insert(trip) != 1 || trip.getTripId() == null) {
            throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
        }

        List<TripDayDTO> days = tripCreationFactory.createDays(trip.getTripId(), request, dayCount);
        if (tripDayDAO.insertAll(days) != dayCount) {
            throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
        }

        // 생성 범위에 여행 스타일·추천 결과 등이 추가되면 저장 순서와 보상 규칙을
        // TripCreationCoordinator 같은 별도 컴포넌트로 분리하고 이 Service는 트랜잭션 경계만 유지한다.
        return new TripCreateResult(trip.getTripId(), dayCount);
    }

    public TripDTO get(Long userId, Long tripId) {
        return guard.requireOwnedTrip(userId, tripId);
    }

    public List<TripDTO> getByUser(Long userId) {
        validateUserId(userId);
        return tripDAO.findByUserId(userId);
    }

    @Transactional
    public Long createDay(Long userId, TripDayDTO tripDay) {
        return tripDayService.create(userId, tripDay);
    }

    public List<TripDayDTO> getDays(Long userId, Long tripId) {
        return tripDayService.getByTrip(userId, tripId);
    }

    @Transactional
    public void updateDay(Long userId, TripDayDTO tripDay) {
        tripDayService.update(userId, tripDay);
    }

    @Transactional
    public void deleteDay(Long userId, Long tripId, Long tripDayId) {
        tripDayService.delete(userId, tripId, tripDayId);
    }

    @Transactional
    public Long createItem(Long userId, Long tripDayId, org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO item) {
        // 경로의 tripDayId가 항상 우선한다. 호출자가 item.tripDayId를 미리 맞춰 보냈더라도 여기서 덮어써서
        // 두 값이 어긋날 여지를 없앤다.
        item.setTripDayId(tripDayId);
        return itineraryItemService.create(userId, item);
    }

    public List<org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO> getItems(Long userId, Long tripDayId) {
        return itineraryItemService.getByTripDay(userId, tripDayId);
    }

    @Transactional
    public void updateItem(Long userId, org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO item) {
        itineraryItemService.update(userId, item);
    }

    @Transactional
    public List<ItineraryItemDTO> updateScheduleTimes(
            Long userId,
            Long tripDayId,
            ItineraryTimeBatchUpdateRequest request) {
        return itineraryItemService.updateScheduleTimes(userId, tripDayId, request);
    }

    @Transactional
    public void reorderItems(Long userId, Long tripDayId, List<Long> itemIds) {
        itineraryItemService.reorder(userId, tripDayId, itemIds);
    }

    @Transactional
    public void deleteItem(Long userId, Long tripDayId, Long itemId) {
        itineraryItemService.delete(userId, tripDayId, itemId);
    }

    @Transactional
    public void update(Long userId, TripDTO trip) {
        TripDTO savedTrip = guard.requireOwnedTrip(userId, trip.getTripId());
        validateDates(trip);
        tripDayService.ensureNoPeriodConflict(savedTrip, trip);
        if (tripDAO.update(trip) == 0) {
            throw new IllegalArgumentException("수정할 여행을 찾을 수 없습니다. tripId=" + trip.getTripId());
        }
        tripDayService.reconcilePeriod(savedTrip, trip);
    }

    @Transactional
    public void delete(Long userId, Long tripId) {
        guard.requireOwnedTrip(userId, tripId);
        if (tripDAO.softDelete(tripId) == 0) {
            throw new IllegalArgumentException("삭제할 여행을 찾을 수 없습니다. tripId=" + tripId);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void validateDates(TripDTO trip) {
        LocalDate startDate = trip.getStartDate();
        LocalDate endDate = trip.getEndDate();

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일은 필수입니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

}
