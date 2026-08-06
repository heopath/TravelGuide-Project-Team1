package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.type.CompanionType;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    void createUsesAuthenticatedUserId() {
        TripCreateRequest request = new TripCreateRequest(
                "서울 여행",
                "서울",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                CompanionType.COUPLE,
                2,
                BigDecimal.valueOf(300_000)
        );
        TripCreateResult result = new TripCreateResult(10L, 3);
        when(tripService.create(42L, request)).thenReturn(result);

        assertThat(tripController.create(principal, request).getBody().data()).isSameAs(result);
        verify(tripService).create(42L, request);
    }

    @Test
    void anonymousListIsRejected() {
        assertThatThrownBy(() -> tripController.getByUser(null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
