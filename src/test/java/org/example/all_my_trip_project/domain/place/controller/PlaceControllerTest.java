package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    @Mock
    private PlaceService placeService;

    @InjectMocks
    private PlaceController placeController;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");

    @Test
    void listUsesAuthenticatedUserForFavoriteOrdering() {
        when(placeService.getPage(42L, 0, 20)).thenReturn(List.of());

        assertThat(placeController.list(principal, 0, 20, null, null, null, null))
                .isEmpty();

        verify(placeService).getPage(42L, 0, 20);
    }

    @Test
    void anonymousListKeepsPublicPlaceLookup() {
        when(placeService.getPage(null, 0, 20)).thenReturn(List.of());

        assertThat(placeController.list(null, 0, 20, null, null, null, null))
                .isEmpty();

        verify(placeService).getPage(null, 0, 20);
    }

    @Test
    void searchUsesAuthenticatedUserForFavoriteOrdering() {
        when(placeService.search(42L, "서울", "CAFE", null, null, 0, 20))
                .thenReturn(List.of());

        assertThat(placeController.list(principal, 0, 20, "서울", "CAFE", null, null))
                .isEmpty();

        verify(placeService).search(42L, "서울", "CAFE", null, null, 0, 20);
    }
}
