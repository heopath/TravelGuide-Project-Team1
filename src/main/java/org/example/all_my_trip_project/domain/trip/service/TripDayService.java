package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!ui")
@Transactional(readOnly = true)
@RequiredArgsConstructor
class TripDayService {
    private final TripDayDAO tripDayDAO;
    private final TripOwnershipGuard ownershipGuard;
    private final TripDayValidator tripDayValidator;
    private final TripPeriodChangeValidator periodChangeValidator;
    private final TripDayReconciler tripDayReconciler;

    @Transactional
    public Long create(Long userId, TripDayDTO tripDay) {
        TripDTO trip = ownershipGuard.requireOwnedTrip(userId, tripDay.getTripId());
        List<TripDayDTO> existingDays = tripDayDAO.findByTripId(tripDay.getTripId());
        if (existingDays.size() >= TripPolicy.MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("여행 일자는 최대 30개까지 등록할 수 있습니다.");
        }
        tripDayValidator.validate(trip, tripDay, existingDays, null);
        tripDayDAO.insert(tripDay);
        return tripDay.getTripDayId();
    }

    public List<TripDayDTO> getByTrip(Long userId, Long tripId) {
        ownershipGuard.requireOwnedTrip(userId, tripId);
        return tripDayDAO.findByTripId(tripId);
    }

    @Transactional
    public void update(Long userId, TripDayDTO tripDay) {
        TripDTO trip = ownershipGuard.requireOwnedDay(userId, tripDay.getTripId(), tripDay.getTripDayId());
        tripDayValidator.validate(trip, tripDay, tripDayDAO.findByTripId(tripDay.getTripId()), tripDay.getTripDayId());
        if (tripDayDAO.update(tripDay) == 0) {
            throw new IllegalArgumentException("수정할 여행 일자를 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(Long userId, Long tripId, Long tripDayId) {
        ownershipGuard.requireOwnedDay(userId, tripId, tripDayId);
        if (tripDayDAO.delete(tripDayId) == 0) {
            throw new IllegalArgumentException("삭제할 여행 일자를 찾을 수 없습니다.");
        }
    }

    /**
     * 기간 변경 범위 밖 일차에 일정이 남아 있으면 거절한다. {@link TripService#update}가 여행 기본정보를
     * 저장하기 전에 호출해 충돌을 커밋 이전에 확정한다.
     */
    void ensureNoPeriodConflict(TripDTO savedTrip, TripDTO requestedTrip) {
        periodChangeValidator.validate(savedTrip, requestedTrip);
    }

    /**
     * 기간이 실제로 바뀐 경우에만 일차를 재조정한다. {@link TripService#update}가 여행 기본정보를
     * 저장한 뒤 같은 트랜잭션에서 호출한다.
     */
    void reconcilePeriod(TripDTO savedTrip, TripDTO requestedTrip) {
        if (!Objects.equals(savedTrip.getStartDate(), requestedTrip.getStartDate())
                || !Objects.equals(savedTrip.getEndDate(), requestedTrip.getEndDate())) {
            tripDayReconciler.reconcile(savedTrip, requestedTrip);
        }
    }
}
