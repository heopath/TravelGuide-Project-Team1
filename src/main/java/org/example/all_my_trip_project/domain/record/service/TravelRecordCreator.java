package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.CreateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordRepository;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.domain.trip.type.TripStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 완료 여행 소유자만 여행당 기록 1건을 작성할 수 있다는 규칙(trip-service-structure.md)을
 * 여기서 강제한다. 소유권 확인은 trip 도메인의 공개 계약({@link TripService#get})을 그대로 재사용하고
 * record 도메인이 여행 Repository를 직접 참조하지 않는다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
class TravelRecordCreator {

    private final TravelRecordRepository travelRecordRepository;
    private final TripService tripService;

    TravelRecordEntity create(Long userId, CreateTravelRecordRequest request) {
        TripDTO trip = tripService.get(userId, request.tripId());
        if (!TripStatus.COMPLETED.name().equals(trip.getStatus())) {
            throw new BusinessException(ErrorCode.TRIP_NOT_COMPLETED);
        }
        if (travelRecordRepository.existsByTripIdAndDeletedAtIsNull(request.tripId())) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
        }

        TravelRecordEntity record = TravelRecordEntity.create(
                request.tripId(),
                userId,
                request.title().trim(),
                request.content(),
                request.rating(),
                request.visibility()
        );
        return travelRecordRepository.save(record);
    }
}
