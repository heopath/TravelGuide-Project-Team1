package org.example.all_my_trip_project.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 운영 지표 중 DB에서 세는 값들. 오류율은 MeterRegistry에서 따로 읽는다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperationCountsDTO {
    private long todayReservations;
    private long openInquiries;
    private long lowStockSlots;
}
