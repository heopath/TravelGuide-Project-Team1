package org.example.all_my_trip_project.domain.booking.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 실습 예약의 최종 확정 상태와 아직 필요한 선택. */
public record BookingConfirmationResponse(
        boolean confirmed,
        boolean eligible,
        OffsetDateTime confirmedAt,
        List<String> missingSteps,
        String redirectUrl
) {}
