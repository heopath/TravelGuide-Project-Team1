package org.example.all_my_trip_project.domain.social.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.all_my_trip_project.domain.social.policy.ReportPolicy;
import org.example.all_my_trip_project.domain.social.type.ReportReason;

public record ReportRecordRequest(
        @NotNull ReportReason reason,
        @Size(max = ReportPolicy.MAX_DETAIL_LENGTH) String detail
) {
}
