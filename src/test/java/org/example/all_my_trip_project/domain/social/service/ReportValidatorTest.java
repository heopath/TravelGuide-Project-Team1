package org.example.all_my_trip_project.domain.social.service;

import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportValidatorTest {

    private final ReportValidator validator = new ReportValidator();

    @Test
    void acceptsResolvedAndRejected() {
        assertThatCode(() -> validator.validateTargetStatus(ReportStatus.RESOLVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTargetStatus(ReportStatus.REJECTED)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPendingAndReviewingAsTargetStatus() {
        assertThatThrownBy(() -> validator.validateTargetStatus(ReportStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REPORT_STATUS_TRANSITION);

        assertThatThrownBy(() -> validator.validateTargetStatus(ReportStatus.REVIEWING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REPORT_STATUS_TRANSITION);
    }
}
