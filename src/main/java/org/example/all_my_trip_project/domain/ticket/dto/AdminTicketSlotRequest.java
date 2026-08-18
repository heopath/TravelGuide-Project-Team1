package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 시간대 등록 요청. 하루짜리와 기간 반복을 같은 모양으로 받는다.
 *
 * <p>기간을 나누지 않은 이유는 실제 등록이 대부분 반복이기 때문이다. 한 달 판매를 열려면
 * 하루씩 30번을 눌러야 하는데, 그러면 중간에 빠뜨린 날이 생겨도 아무도 알아채지 못한다.
 *
 * <p>{@code usageEndDate}가 없으면 {@code usageStartDate} 하루만 만든다.
 * {@code weekdays}가 비어 있으면 기간 안의 모든 날을 만든다.
 */
public record AdminTicketSlotRequest(
        @NotNull(message = "옵션을 선택해 주세요.")
        Long ticketProductOptionId,

        @NotNull(message = "이용 시작일을 입력해 주세요.")
        LocalDate usageStartDate,

        LocalDate usageEndDate,

        /** 비어 있으면 모든 요일. 값이 있으면 그 요일에만 만든다. */
        Set<DayOfWeek> weekdays,

        /**
         * 종일권이면 비운다. {@code uk_ticket_time_slots}가 NULLS NOT DISTINCT라
         * 같은 날 종일권은 하나만 만들어진다.
         */
        LocalTime startTime,

        LocalTime endTime,

        @NotNull(message = "판매 수량을 입력해 주세요.")
        @Min(value = 0, message = "판매 수량은 0장 이상이어야 합니다.")
        Integer totalQuantity
) {
    /** 끝날이 없으면 하루짜리다. */
    public LocalDate effectiveEndDate() {
        return usageEndDate == null ? usageStartDate : usageEndDate;
    }
}
