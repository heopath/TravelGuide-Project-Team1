package org.example.all_my_trip_project.domain.trip.service.support;

import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.domain.trip.type.TripSource;
import org.example.all_my_trip_project.domain.trip.type.TripStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TripCreationFactory {

    public TripDTO createTrip(Long userId, TripCreateRequest request) {
        String title = request.title() == null || request.title().isBlank()
                ? request.destinationName().trim() + TripPolicy.DEFAULT_TITLE_SUFFIX
                : request.title().trim();

        return TripDTO.builder()
                .userId(userId)
                .title(title)
                .destinationName(request.destinationName().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .companionType(request.companionType().name())
                .companionCount(request.companionCount())
                .budgetAmount(request.budgetAmount())
                .currencyCode(TripPolicy.DEFAULT_CURRENCY_CODE)
                .status(TripStatus.DRAFT.name())
                .source(TripSource.MANUAL.name())
                .build();
    }

    public List<TripDayDTO> createDays(Long tripId, TripCreateRequest request, int dayCount) {
        List<TripDayDTO> days = new ArrayList<>(dayCount);
        for (int index = 0; index < dayCount; index++) {
            int dayNumber = index + 1;
            days.add(TripDayDTO.builder()
                    .tripId(tripId)
                    .dayNumber(dayNumber)
                    .tripDate(request.startDate().plusDays(index))
                    .title("DAY " + dayNumber)
                    .build());
        }
        return List.copyOf(days);
    }
}
