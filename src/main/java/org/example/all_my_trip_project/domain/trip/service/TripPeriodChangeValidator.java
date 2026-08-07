package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class TripPeriodChangeValidator {
    private final TripDayDAO tripDayDAO;

    void validate(TripDTO savedTrip, TripDTO requestedTrip) {
        LocalDate startDate = requestedTrip.getStartDate();
        LocalDate endDate = requestedTrip.getEndDate();

        boolean periodChanged =
            !savedTrip.getStartDate().equals(startDate)
                || !savedTrip.getEndDate().equals(endDate);

        if (!periodChanged) {
            return;
        }

        if (tripDayDAO.existsOutsidePeriodWithItineraryItems(savedTrip.getTripId(), startDate, endDate)) {
            throw new BusinessException(ErrorCode.TRIP_PERIOD_CONFLICT);
        }
    }
}
