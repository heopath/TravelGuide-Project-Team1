package org.example.all_my_trip_project.domain.social.service;

import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!ui")
class ReportValidator {

    void validateTargetStatus(ReportStatus targetStatus) {
        if (targetStatus != ReportStatus.REVIEWING
                && targetStatus != ReportStatus.RESOLVED
                && targetStatus != ReportStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_STATUS_TRANSITION);
        }
    }
}
