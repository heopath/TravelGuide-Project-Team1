package org.example.all_my_trip_project.domain.flight.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 자가 신고. 결제 확인이 아니다.
 *
 * @param clickId 이 신고가 어느 이탈 건에 대한 응답인지. 없으면 결과를 기록하지 않는다.
 */
public record ReportBookedRequest(
        @NotNull Boolean userReportedBooked,
        Long clickId
) {}
