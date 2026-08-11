package org.example.all_my_trip_project.domain.social.service;

import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.type.ReportReason;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportProcessorTest {

    private final ReportProcessor processor = new ReportProcessor();

    @Test
    void resolvesAPendingReport() {
        TravelRecordReportEntity report =
                TravelRecordReportEntity.create(1L, 9L, ReportReason.INAPPROPRIATE, "설명");

        processor.process(report, 5L, ReportStatus.RESOLVED, "조치 완료");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED.name());
        assertThat(report.getProcessedBy()).isEqualTo(5L);
        assertThat(report.getProcessedAt()).isNotNull();
        assertThat(report.getResolutionNote()).isEqualTo("조치 완료");
    }

    @Test
    void rejectsProcessingAReportThatIsAlreadyResolved() {
        TravelRecordReportEntity report =
                TravelRecordReportEntity.create(1L, 9L, ReportReason.SPAM, null);
        processor.process(report, 5L, ReportStatus.REJECTED, "근거 부족");

        assertThatThrownBy(() -> processor.process(report, 5L, ReportStatus.RESOLVED, "다시 처리"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REPORT_STATUS_TRANSITION);
    }
}
