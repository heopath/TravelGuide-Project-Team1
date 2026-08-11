package org.example.all_my_trip_project.domain.accommodation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 자가 신고. 결제 확인이 아니다.
 *
 * <p>{@code userReportedBooked=false}는 "아니요"가 아니라 "나중에 확인할게요"다.
 * 담아둔 숙소는 그대로 두고 예약 표시만 하지 않는다. 선택 자체를 되돌리는 것은 삭제 API다.
 *
 * @param clickId 이 신고가 어느 이탈 건에 대한 응답인지. 없으면 결과를 기록하지 않는다
 */
public record ReportAccommodationBookedRequest(
        @NotNull Boolean userReportedBooked,
        Long clickId
) {}
