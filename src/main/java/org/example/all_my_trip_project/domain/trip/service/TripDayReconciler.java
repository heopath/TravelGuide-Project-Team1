package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class TripDayReconciler {
    private final TripDayDAO tripDayDAO;

    void reconcile(TripDTO savedTrip, TripDTO requestedTrip) {
        Long tripId = savedTrip.getTripId();
        LocalDate startDate = requestedTrip.getStartDate();
        LocalDate endDate = requestedTrip.getEndDate();

        List<TripDayDTO> existingDays = new ArrayList<>(tripDayDAO.findByTripId(tripId));

        List<TripDayDTO> retainedDays = new ArrayList<>();
        for (TripDayDTO day : existingDays) {
            LocalDate date = day.getTripDate();
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                tripDayDAO.delete(day.getTripDayId());
            } else {
                retainedDays.add(day);
            }
        }

        // trip_days에는 즉시 검사되는 UNIQUE(trip_id, day_number) 제약이 있다. 남아있는 일차의 day_number를
        // 새 시작일 기준 최종값으로 바로 바꾸면, 아직 처리하지 않은 다른 일차가 그 번호를 들고 있어 충돌할 수 있다
        // (예: 8/12~8/14 → 8/10~8/12 변경 시 1일차를 3으로 바꾸는 순간 기존 3일차가 아직 3을 갖고 있음).
        // 그래서 sort_order 재정렬과 같은 방식으로 먼저 최종 범위(1..MAX_TRIP_DAYS) 밖의 임시 번호로
        // 모두 옮긴 뒤에 최종 day_number를 부여한다.
        int temporaryDayNumber = TripPolicy.MAX_TRIP_DAYS + 1;
        for (TripDayDTO day : retainedDays) {
            day.setDayNumber(temporaryDayNumber++);
            tripDayDAO.update(day);
        }

        Set<LocalDate> retainedDates = new HashSet<>(retainedDays.size());
        for (TripDayDTO day : retainedDays) {
            int newDayNumber = (int) ChronoUnit.DAYS.between(startDate, day.getTripDate()) + 1;
            day.setDayNumber(newDayNumber);
            tripDayDAO.update(day);
            retainedDates.add(day.getTripDate());
        }

        int dayCount = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        for (int index = 0; index < dayCount; index++) {
            LocalDate date = startDate.plusDays(index);
            if (!retainedDates.contains(date)) {
                tripDayDAO.insert(TripDayDTO.builder()
                    .tripId(tripId)
                    .dayNumber(index + 1)
                    .tripDate(date)
                    .title("DAY " + (index + 1))
                    .build());
            }
        }
    }
}
