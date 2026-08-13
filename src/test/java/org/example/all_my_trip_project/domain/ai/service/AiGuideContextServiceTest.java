package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.domain.user.dto.UserPreferenceResponse;
import org.example.all_my_trip_project.domain.user.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGuideContextServiceTest {
    private final ObjectProvider<MemberService> memberServiceProvider = mock(ObjectProvider.class);
    private final ObjectProvider<TripService> tripServiceProvider = mock(ObjectProvider.class);
    private final TripService tripService = mock(TripService.class);
    private final MemberService memberService = mock(MemberService.class);
    private final AiGuideContextService service = new AiGuideContextService(
            tripServiceProvider, memberServiceProvider
    );

    @Test
    void loadsOnlyTheSignedInUsersExistingTripDaysItemsAndPreferences() {
        TripDTO trip = TripDTO.builder().tripId(12L).userId(1L).destinationName("Busan")
                .startDate(LocalDate.of(2026, 8, 10)).endDate(LocalDate.of(2026, 8, 11))
                .foodPreference("SEAFOOD").build();
        TripDayDTO day = TripDayDTO.builder().tripDayId(101L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 10)).title("Arrival").build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(101L).placeId(55L).title("Gwangalli dinner")
                .startTime(LocalTime.of(18, 0)).itemType("FOOD").memo("Seafood restaurant").build();
        when(tripServiceProvider.getIfAvailable()).thenReturn(tripService);
        when(memberServiceProvider.getIfAvailable()).thenReturn(memberService);
        when(memberService.getPreferences(1L)).thenReturn(new UserPreferenceResponse(List.of(
                new UserPreferenceResponse.PreferenceItem((short) 1, "FOOD", "Food travel", (short) 5, "USER")
        )));
        when(tripService.get(1L, 12L)).thenReturn(trip);
        when(tripService.getDays(1L, 12L)).thenReturn(List.of(day));
        when(tripService.getItems(1L, 101L)).thenReturn(List.of(item));

        AiGuideContext context = service.load(1L, new AiGuideRequest("Recommend dinner", 12L));

        assertThat(context.trip().destinationName()).isEqualTo("Busan");
        assertThat(context.trip().days()).extracting(AiGuideContext.Day::title).containsExactly("Arrival");
        assertThat(context.trip().days().getFirst().items()).extracting(AiGuideContext.Item::title)
                .containsExactly("Gwangalli dinner");
        assertThat(context.trip().days().getFirst().items()).extracting(AiGuideContext.Item::placeId)
                .containsExactly(55L);
        assertThat(context.preferences()).extracting(AiGuideContext.Preference::name).containsExactly("Food travel");
        verify(tripService).get(1L, 12L);
        verify(tripService).getDays(1L, 12L);
        verify(tripService).getItems(1L, 101L);
    }

    @Test
    void propagatesTripNotFoundWithoutLoadingDaysOrItems() {
        when(tripServiceProvider.getIfAvailable()).thenReturn(tripService);
        when(memberServiceProvider.getIfAvailable()).thenReturn(memberService);
        when(memberService.getPreferences(1L)).thenReturn(new UserPreferenceResponse(List.of()));
        when(tripService.get(1L, 99L)).thenThrow(new IllegalArgumentException("Trip not found"));

        assertThatThrownBy(() -> service.load(1L, new AiGuideRequest("Recommend dinner", 99L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tripService, never()).getDays(1L, 99L);
    }
}
