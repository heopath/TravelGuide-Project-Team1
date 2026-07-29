package org.example.all_my_trip_project.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDayDTO {
    private Long tripDayId;
    private Long tripId;
    private Integer dayNumber;
    private LocalDate tripDate;
    private String title;
    private String memo;
    // PostgreSQL TIMESTAMPTZ의 UTC offset을 보존하기 위한 타입이며 DTO 필드명은 기존과 동일하다.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
