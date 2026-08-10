package org.example.all_my_trip_project.domain.social.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.social.dto.ProcessReportRequest;
import org.example.all_my_trip_project.domain.social.dto.ReportRecordRequest;
import org.example.all_my_trip_project.domain.social.dto.TravelRecordReportResponse;
import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.type.ReportReason;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 여행 기록 신고 도메인의 외부 공개 진입점이다. 접수·조회·처리는 각각의 협력 클래스로 위임하고
 * 이 클래스는 트랜잭션 경계와 호출 순서만 조율한다. 관리자 권한 자체(로그인한 사용자가 ADMIN인지)는
 * 아직 별도 AdminService가 없어 컨트롤러에서 {@code AuthenticatedUser.role()}로 먼저 걸러낸다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordReportService {

    private final ActiveMemberGuard activeMemberGuard;
    private final ReportValidator validator;
    private final ReportCreator creator;
    private final ReportReader reader;
    private final ReportProcessor processor;

    @Transactional
    public TravelRecordReportResponse report(Long reporterUserId, Long travelRecordId, ReportRecordRequest request) {
        validateUserId(reporterUserId);
        activeMemberGuard.requireActiveMember(reporterUserId);
        TravelRecordReportEntity report = creator.create(reporterUserId, travelRecordId, request);
        return toResponse(report);
    }

    public List<TravelRecordReportResponse> getReports(Long adminUserId, ReportStatus status) {
        validateUserId(adminUserId);
        return reader.findByStatus(status).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TravelRecordReportResponse process(Long adminUserId, Long reportId, ProcessReportRequest request) {
        validateUserId(adminUserId);
        validator.validateTargetStatus(request.status());
        TravelRecordReportEntity report = reader.findById(reportId);
        String resolutionNote = normalizeResolutionNote(request.resolutionNote());
        processor.process(report, adminUserId, request.status(), resolutionNote);
        return toResponse(report);
    }

    private String normalizeResolutionNote(String resolutionNote) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            return null;
        }
        return resolutionNote.trim();
    }

    private TravelRecordReportResponse toResponse(TravelRecordReportEntity report) {
        return new TravelRecordReportResponse(
                report.getTravelRecordReportId(),
                report.getTravelRecordId(),
                report.getReporterUserId(),
                ReportReason.valueOf(report.getReason()),
                report.getDetail(),
                ReportStatus.valueOf(report.getStatus()),
                report.getProcessedBy(),
                report.getProcessedAt(),
                report.getResolutionNote(),
                report.getCreatedAt()
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
