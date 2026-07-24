package org.example.all_my_trip_project.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
