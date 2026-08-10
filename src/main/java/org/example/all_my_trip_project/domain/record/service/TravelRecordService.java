package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.CreateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordResponse;
import org.example.all_my_trip_project.domain.record.dto.UpdateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 여행 기록 도메인의 외부 공개 진입점이다. 검증·생성·조회·수정·삭제는 각각의 협력 클래스로 위임하고
 * 이 클래스는 트랜잭션 경계와 소유권 확인 순서만 조율한다(trip 도메인의 {@code TripService} 구조를 따른다).
 * 다른 도메인(social 등)이 여행 기록의 존재·공개 여부를 확인할 때 쓰는 {@link TravelRecordAccessGuard}
 * 계약도 이 클래스가 구현한다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordService implements TravelRecordAccessGuard {

    private final ActiveMemberGuard activeMemberGuard;
    private final TravelRecordValidator validator;
    private final TravelRecordCreator creator;
    private final TravelRecordReader reader;
    private final TravelRecordModifier modifier;
    private final TravelRecordRemover remover;
    private final TravelRecordImageReplacer imageReplacer;
    private final TravelRecordResponseMapper responseMapper;

    @Transactional
    public TravelRecordResponse create(Long userId, CreateTravelRecordRequest request) {
        validateUserId(userId);
        activeMemberGuard.requireActiveMember(userId);
        TravelRecordEntity record = creator.create(userId, request);
        return responseMapper.toResponse(record, List.of());
    }

    public TravelRecordResponse get(Long viewerUserId, Long travelRecordId) {
        return reader.get(viewerUserId, travelRecordId);
    }

    public List<TravelRecordResponse> getMyRecords(Long userId) {
        validateUserId(userId);
        return reader.getMyRecords(userId);
    }

    @Transactional
    public TravelRecordResponse update(Long userId, Long travelRecordId, UpdateTravelRecordRequest request) {
        TravelRecordEntity record = reader.findOwned(userId, travelRecordId);
        modifier.update(record, request);
        return responseMapper.toResponse(record, reader.findImages(travelRecordId));
    }

    @Transactional
    public TravelRecordResponse replaceImages(
            Long userId,
            Long travelRecordId,
            ReplaceRecordImagesRequest request
    ) {
        TravelRecordEntity record = reader.findOwned(userId, travelRecordId);
        validator.validateImages(request);
        imageReplacer.replace(travelRecordId, request);
        List<TravelRecordImageEntity> images = reader.findImages(travelRecordId);
        return responseMapper.toResponse(record, images);
    }

    @Transactional
    public void delete(Long userId, Long travelRecordId) {
        TravelRecordEntity record = reader.findOwned(userId, travelRecordId);
        remover.remove(record);
    }

    @Override
    public TravelRecordAccessView requireAccessibleRecord(Long viewerUserId, Long travelRecordId) {
        return reader.getAccessView(viewerUserId, travelRecordId);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
