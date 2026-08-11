package org.example.all_my_trip_project.domain.booking.dto;

import java.math.BigDecimal;
import java.util.List;

/** 한 여행에 담긴 항공·숙박·티켓을 같은 모양으로 내려주는 응답. */
public record TripBookingSummaryResponse(
        List<BookingItem> items,
        MoneySummary money,
        Progress progress,
        List<SectionError> errors
) {
    public record BookingItem(
            String type,
            String referenceId,
            Integer leg,
            String title,
            String detail,
            String status,
            String statusLabel,
            BigDecimal amount,
            String currency,
            String amountSource,
            boolean includedInEstimate,
            boolean practice,
            String bookingRef,
            String usageDate,
            Integer quantity
    ) {}

    /**
     * estimatedTotal은 화면 비교를 위한 스냅샷 합계이며 실제 결제액이 아니다.
     * practiceTotal은 그중 샘플·Sandbox·내부 모의 예약 금액이다.
     */
    public record MoneySummary(
            BigDecimal estimatedTotal,
            BigDecimal practiceTotal,
            String currency,
            int excludedItemCount,
            boolean actualPaymentConfirmed
    ) {}

    public record Progress(int done, int total) {}

    /** 한 종류 조회가 실패해도 다른 종류는 표시하기 위한 부분 실패 정보. */
    public record SectionError(String section, String message) {}
}
