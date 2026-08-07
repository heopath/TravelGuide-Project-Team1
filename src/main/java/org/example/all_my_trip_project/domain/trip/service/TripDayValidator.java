package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
@Profile("!ui")
class TripDayValidator {

    public void validate(TripDTO trip, TripDayDTO candidate, List<TripDayDTO> existingDays, Long excludedDayId) {
        if (candidate.getDayNumber() == null || candidate.getTripDate() == null) {
            throw new IllegalArgumentException("dayNumber와 tripDate는 필수입니다.");
        }
        long tripLength = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        if (candidate.getDayNumber() < 1 || candidate.getDayNumber() > tripLength
                || candidate.getDayNumber() > TripPolicy.MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("dayNumber는 여행 기간 안의 일차여야 합니다.");
        }
        if (candidate.getTripDate().isBefore(trip.getStartDate())
                || candidate.getTripDate().isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException("tripDate는 여행 시작일과 종료일 사이여야 합니다.");
        }
        if (existingDays.stream()
                .filter(day -> !Objects.equals(day.getTripDayId(), excludedDayId))
                .anyMatch(day -> Objects.equals(day.getDayNumber(), candidate.getDayNumber())
                        || Objects.equals(day.getTripDate(), candidate.getTripDate()))) {
            throw new IllegalArgumentException("dayNumber와 tripDate는 여행 안에서 중복될 수 없습니다.");
        }
    }
}
