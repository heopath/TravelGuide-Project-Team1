package org.example.all_my_trip_project.domain.social.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.service.TravelRecordAccessGuard;
import org.example.all_my_trip_project.domain.social.dto.ReportRecordRequest;
import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.repository.TravelRecordReportRepository;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 신고 대상 여행 기록이 실제로 존재하고 신고자가 볼 수 있는지는 record 도메인의 공개 계약
 * ({@link TravelRecordAccessGuard})으로만 확인한다. social 도메인은 record 도메인의 Repository나
 * Entity를 직접 참조하지 않는다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
class ReportCreator {

    private static final List<String> OPEN_STATUSES = List.of(
            ReportStatus.PENDING.name(),
            ReportStatus.REVIEWING.name()
    );

    private final TravelRecordReportRepository travelRecordReportRepository;
    private final TravelRecordAccessGuard travelRecordAccessGuard;

    TravelRecordReportEntity create(Long reporterUserId, Long travelRecordId, ReportRecordRequest request) {
        TravelRecordAccessView record =
                travelRecordAccessGuard.requireAccessibleRecord(reporterUserId, travelRecordId);

        boolean alreadyOpen = travelRecordReportRepository.existsByTravelRecordIdAndReporterUserIdAndStatusIn(
                record.travelRecordId(), reporterUserId, OPEN_STATUSES);
        if (alreadyOpen) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PENDING);
        }

        String detail = normalizeDetail(request.detail());
        TravelRecordReportEntity report = TravelRecordReportEntity.create(
                record.travelRecordId(), reporterUserId, request.reason(), detail);
        return travelRecordReportRepository.save(report);
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.trim();
    }
}
