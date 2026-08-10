package org.example.all_my_trip_project.domain.social.dto;

import org.example.all_my_trip_project.domain.social.type.ReportReason;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;

import java.time.OffsetDateTime;

public record TravelRecordReportResponse(
        Long travelRecordReportId,
        Long travelRecordId,
        Long reporterUserId,
        ReportReason reason,
        String detail,
        ReportStatus status,
        Long processedBy,
        OffsetDateTime processedAt,
        String resolutionNote,
        OffsetDateTime createdAt
) {
}
