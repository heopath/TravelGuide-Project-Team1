package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.service.TripService;
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
class TripControllerTest {

    @Mock
    private TripService tripService;

    @InjectMocks
    private TripController tripController;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");

    @Test
    void listUsesAuthenticatedUserId() {
        when(tripService.getByUser(42L)).thenReturn(List.of());

        assertThat(tripController.getByUser(principal).data()).isEmpty();

        verify(tripService).getByUser(42L);
    }

    @Test
    void createOverridesClientUserIdWithAuthenticatedUserId() {
        TripDTO request = TripDTO.builder().userId(999L).title("서울 여행").build();
        TripDTO saved = TripDTO.builder().tripId(10L).userId(42L).title("서울 여행").build();
        TripCreateResult result = new TripCreateResult(saved, List.of());
        when(tripService.createWithDays(42L, request)).thenReturn(result);

        assertThat(tripController.create(principal, request).getBody().data()).isSameAs(result);
        verify(tripService).createWithDays(42L, request);
    }

    @Test
    void anonymousListIsRejected() {
        assertThatThrownBy(() -> tripController.getByUser(null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
