package org.example.all_my_trip_project.domain.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 한 사람이 예약한 항공·숙소·티켓을 여행에 상관없이 한 줄로 모은 것.
 *
 * <p>여행별 요약({@link TripBookingSummaryResponse})은 "이 여행이 어떻게 됐나"를 보는
 * 길이고, 이쪽은 "내가 뭘 예약했나"를 종류별로 보는 길이다. 같은 자료를 다르게 묶는다.
 *
 * <p>각 줄에 여행을 함께 적는다. 종류별로 모아 놓으면 어느 여행 것인지가 사라져,
 * 부산 항공권과 제주 항공권이 나란히 놓여도 구분할 수 없다.
 */
public record MyBookingsResponse(
        List<Entry> items,
        Counts counts
) {
    public record Entry(
            Long tripId,
            String tripTitle,
            String type,
            String referenceId,
            String title,
            String detail,
            String status,
            String statusLabel,
            BigDecimal amount,
            String currency,
            /** 실습·샘플 금액이면 참. 화면에서 이 표시를 떼면 안 된다. */
            boolean practice,
            String usageDate,
            Integer quantity,
            OffsetDateTime bookedAt
    ) {}

    /** 종류별 개수. 탭에 붙일 숫자를 화면에서 다시 세지 않게 한다. */
    public record Counts(int total, int flight, int hotel, int ticket) {}
}
