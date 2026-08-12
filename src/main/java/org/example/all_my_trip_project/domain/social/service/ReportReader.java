package org.example.all_my_trip_project.domain.social.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.repository.TravelRecordReportRepository;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class ReportReader {

    private final TravelRecordReportRepository travelRecordReportRepository;

    TravelRecordReportEntity findById(Long reportId) {
        return travelRecordReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    List<TravelRecordReportEntity> findByStatus(ReportStatus status) {
        if (status == null) {
            return travelRecordReportRepository.findAllByOrderByCreatedAtAsc();
        }
        return travelRecordReportRepository.findByStatusOrderByCreatedAtAsc(status.name());
    }
}
