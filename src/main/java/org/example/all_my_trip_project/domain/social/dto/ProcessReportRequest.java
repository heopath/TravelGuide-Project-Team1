package org.example.all_my_trip_project.domain.social.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.social.policy.ReportPolicy;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;

public record ProcessReportRequest(
        @NotNull ReportStatus status,
        @Size(max = ReportPolicy.MAX_RESOLUTION_NOTE_LENGTH) String resolutionNote
) {
}
