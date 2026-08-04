package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.dao.UserDAO;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.service.support.TripCreateValidator;
import org.example.all_my_trip_project.domain.trip.service.support.TripCreationFactory;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {
    private final TripDAO tripDAO;
    private final TripDayDAO tripDayDAO;
    private final UserDAO userDAO;
    private final TripCreateValidator tripCreateValidator;
    private final TripCreationFactory tripCreationFactory;

    @Transactional
    public TripCreateResult create(Long userId, TripCreateRequest request) {
        requireActiveUser(userId);
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
        return requireOwnedTrip(userId, tripId);
    }

    public List<TripDTO> getByUser(Long userId) {
        validateUserId(userId);
        return tripDAO.findByUserId(userId);
    }

    @Transactional
    public void update(Long userId, TripDTO trip) {
        TripDTO savedTrip = requireOwnedTrip(userId, trip.getTripId());
        validateDates(trip);
        if (tripDAO.update(trip) == 0) {
            throw new IllegalArgumentException("수정할 여행을 찾을 수 없습니다. tripId=" + trip.getTripId());
        }
        if (!Objects.equals(savedTrip.getStartDate(), trip.getStartDate())
                || !Objects.equals(savedTrip.getEndDate(), trip.getEndDate())) {
            synchronizeDays(trip);
        }
    }

    @Transactional
    public void delete(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);
        if (tripDAO.softDelete(tripId) == 0) {
            throw new IllegalArgumentException("삭제할 여행을 찾을 수 없습니다. tripId=" + tripId);
        }
    }

    private TripDTO requireOwnedTrip(Long userId, Long tripId) {
        validateUserId(userId);
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireActiveUser(Long userId) {
        validateUserId(userId);
        UserDTO user = userDAO.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    private void validateDates(TripDTO trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일은 필수입니다.");
        }
        if (trip.getStartDate() != null && trip.getEndDate() != null
                && trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private void synchronizeDays(TripDTO trip) {
        List<TripDayDTO> existingDays = new ArrayList<>(tripDayDAO.findByTripId(trip.getTripId()));
        tripDayDAO.moveOutOfDateRange(trip.getTripId());

        int desiredCount = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        for (int index = 0; index < desiredCount; index++) {
            int dayNumber = index + 1;
            if (index < existingDays.size()) {
                TripDayDTO day = existingDays.get(index);
                day.setDayNumber(dayNumber);
                day.setTripDate(trip.getStartDate().plusDays(index));
                tripDayDAO.update(day);
            } else {
                tripDayDAO.insert(TripDayDTO.builder()
                        .tripId(trip.getTripId())
                        .dayNumber(dayNumber)
                        .tripDate(trip.getStartDate().plusDays(index))
                        .title("DAY " + dayNumber)
                        .build());
            }
        }
        for (int index = desiredCount; index < existingDays.size(); index++) {
            tripDayDAO.delete(existingDays.get(index).getTripDayId());
        }
    }
}
