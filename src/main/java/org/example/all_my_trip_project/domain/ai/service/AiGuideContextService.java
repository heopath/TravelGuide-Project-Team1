package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.domain.user.dto.UserPreferenceResponse;
import org.example.all_my_trip_project.domain.user.service.MemberService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiGuideContextService {
    private final ObjectProvider<TripService> tripServiceProvider;
    private final ObjectProvider<MemberService> memberServiceProvider;

    public AiGuideContext load(Long userId, AiGuideRequest request) {
        List<AiGuideContext.Preference> preferences = loadPreferences(userId);
        if (request.tripId() == null) return new AiGuideContext(null, preferences);

        TripService tripService = tripServiceProvider.getIfAvailable();
        if (tripService == null) return new AiGuideContext(null, preferences);

        // TripService is the only public entry point for the trip aggregate.
        TripDTO trip = tripService.get(userId, request.tripId());
        List<TripDayDTO> days = tripService.getDays(userId, request.tripId());
        return new AiGuideContext(toTrip(userId, trip, days, tripService), preferences);
    }

    private List<AiGuideContext.Preference> loadPreferences(Long userId) {
        MemberService memberService = memberServiceProvider.getIfAvailable();
        if (memberService == null) return List.of();
        return memberService.getPreferences(userId).preferences().stream().map(this::toPreference).toList();
    }

    private AiGuideContext.Trip toTrip(Long userId, TripDTO trip, List<TripDayDTO> days,
                                       TripService tripService) {
        return new AiGuideContext.Trip(trip.getTripId(), trip.getTitle(), trip.getDestinationName(),
                trip.getStartDate(), trip.getEndDate(), trip.getCompanionType(), trip.getCompanionCount(),
                trip.getPurpose(), trip.getBudgetAmount(), trip.getCurrencyCode(), trip.getTransportPreference(),
                trip.getFoodPreference(), trip.getPace(), trip.getAccommodationStyle(),
                days.stream().map(day -> new AiGuideContext.Day(day.getDayNumber(), day.getTripDate(),
                        day.getTitle(), day.getMemo(), tripService.getItems(userId, day.getTripDayId())
                        .stream().map(this::toItem).toList())).toList());
    }

    private AiGuideContext.Item toItem(ItineraryItemDTO item) {
        return new AiGuideContext.Item(item.getTitle(), item.getStartTime(), item.getEndTime(),
                item.getItemType(), item.getMemo());
    }

    private AiGuideContext.Preference toPreference(UserPreferenceResponse.PreferenceItem preference) {
        return new AiGuideContext.Preference(preference.code(), preference.name(), preference.preferenceScore());
    }
}
