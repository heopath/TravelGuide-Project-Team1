package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordResponse;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordImageRepository;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordRepository;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class TravelRecordReader {

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordImageRepository travelRecordImageRepository;
    private final TravelRecordResponseMapper responseMapper;

    /**
     * PUBLIC 기록은 누구나(비로그인 포함, viewerUserId=null) 볼 수 있고, PRIVATE 기록은 소유자만 볼 수 있다.
     * 존재하지 않거나 볼 수 없는 기록은 노출 여부를 구분하지 않고 동일하게 404로 통일한다.
     */
    TravelRecordEntity findAccessible(Long viewerUserId, Long travelRecordId) {
        TravelRecordEntity record = travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(travelRecordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isPublic() && !record.isOwnedBy(viewerUserId)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return record;
    }

    /**
     * 쓰기 작업 전용 소유권 확인이다. {@code TripOwnershipGuard.requireOwnedTrip()}과 같은 순서로
     * userId 자체의 유효성(미인증)을 먼저 확인한 뒤 존재·소유 여부를 확인한다. 조회수 전용인
     * {@link #findAccessible}은 비로그인 열람(PUBLIC)을 허용해야 하므로 이 검사를 하지 않는다.
     */
    TravelRecordEntity findOwned(Long userId, Long travelRecordId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TravelRecordEntity record = travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(travelRecordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return record;
    }

    TravelRecordResponse get(Long viewerUserId, Long travelRecordId) {
        TravelRecordEntity record = findAccessible(viewerUserId, travelRecordId);
        return responseMapper.toResponse(record, findImages(record.getTravelRecordId()));
    }

    List<TravelRecordResponse> getMyRecords(Long userId) {
        return travelRecordRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(record -> responseMapper.toResponse(record, findImages(record.getTravelRecordId())))
                .toList();
    }

    TravelRecordAccessView getAccessView(Long viewerUserId, Long travelRecordId) {
        TravelRecordEntity record = findAccessible(viewerUserId, travelRecordId);
        return new TravelRecordAccessView(
                record.getTravelRecordId(),
                record.getTripId(),
                record.getUserId(),
                RecordVisibility.valueOf(record.getVisibility())
        );
    }

    List<TravelRecordImageEntity> findImages(Long travelRecordId) {
        return travelRecordImageRepository.findByTravelRecordIdOrderBySortOrderAsc(travelRecordId);
    }
}
