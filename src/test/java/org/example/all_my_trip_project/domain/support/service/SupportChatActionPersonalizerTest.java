package org.example.all_my_trip_project.domain.support.service;

import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class SupportChatActionPersonalizerTest {

    private static final long ROOM_ID = 5L;
    private static final long USER_ID = 7L;

    private final SupportChatDAO dao = mock(SupportChatDAO.class);
    private final TripService tripService = mock(TripService.class);
    private final SupportChatActionPersonalizer personalizer =
            new SupportChatActionPersonalizer(dao, tripService);

    @Test
    void offersCreationAndDiscoveryWhenUserHasNoTrips() {
        givenTrips(List.of());

        List<String> actions = personalizer.personalize(
                ROOM_ID, List.of(user("여행을 만들고 싶어")), List.of("NEW_TRIP"));

        assertThat(actions).containsExactly("NEW_TRIP", "RECOMMENDED_PLACES");
    }

    @Test
    void offersExistingTripChoicesWhenUserAlreadyHasTrips() {
        givenTrips(List.of(TripDTO.builder().tripId(11L).userId(USER_ID).build()));

        List<String> actions = personalizer.personalize(
                ROOM_ID, List.of(user("여행 계획을 세우고 싶어")), List.of("NEW_TRIP"));

        assertThat(actions).containsExactly("NEW_TRIP", "MY_TRIPS", "TRIP_SCHEDULE");
    }

    @Test
    void doesNotQueryTripsForUnrelatedActions() {
        List<String> actions = personalizer.personalize(
                ROOM_ID, List.of(user("비밀번호를 바꾸고 싶어")), List.of("ACCOUNT_SETTINGS"));

        assertThat(actions).containsExactly("ACCOUNT_SETTINGS");
        verifyNoInteractions(dao, tripService);
    }

    @Test
    void fallsBackToOriginalActionsWhenTripLookupFails() {
        when(dao.findRoom(ROOM_ID)).thenThrow(new IllegalStateException("DB unavailable"));

        List<String> actions = personalizer.personalize(
                ROOM_ID, List.of(user("여행을 만들고 싶어")), List.of("NEW_TRIP"));

        assertThat(actions).containsExactly("NEW_TRIP");
    }

    private void givenTrips(List<TripDTO> trips) {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(SupportChatRoomDTO.builder()
                .supportChatRoomId(ROOM_ID).userId(USER_ID).status("BOT").build()));
        when(tripService.getByUser(USER_ID)).thenReturn(trips);
    }

    private static SupportChatMessageDTO user(String content) {
        return SupportChatMessageDTO.builder().senderType("USER").content(content).build();
    }
}
