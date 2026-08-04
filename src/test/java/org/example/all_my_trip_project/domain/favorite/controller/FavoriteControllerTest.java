package org.example.all_my_trip_project.domain.favorite.controller;

import org.example.all_my_trip_project.domain.favorite.service.FavoriteService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private FavoriteController favoriteController;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");

    @Test
    void listUsesAuthenticatedUserId() {
        when(favoriteService.getFavorites(42L, 0, 20)).thenReturn(List.of());

        assertThat(favoriteController.list(principal, 0, 20).data()).isEmpty();

        verify(favoriteService).getFavorites(42L, 0, 20);
    }

    @Test
    void addUsesAuthenticatedUserId() {
        favoriteController.add(principal, 100L, "가고 싶은 곳");

        verify(favoriteService).add(42L, 100L, "가고 싶은 곳");
    }

    @Test
    void countUsesAuthenticatedUserId() {
        when(favoriteService.countFavorites(42L)).thenReturn(3L);

        assertThat(favoriteController.count(principal).data()).isEqualTo(3L);

        verify(favoriteService).countFavorites(42L);
    }

    @Test
    void statusChecksOnlyRequestedPlace() {
        when(favoriteService.isFavorite(42L, 100L)).thenReturn(true);

        assertThat(favoriteController.status(principal, 100L).data().favorite()).isTrue();

        verify(favoriteService).isFavorite(42L, 100L);
    }

    @Test
    void requestWithoutAuthenticationIsRejected() {
        assertThatThrownBy(() -> favoriteController.list(null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
