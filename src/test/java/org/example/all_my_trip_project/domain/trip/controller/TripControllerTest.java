package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.global.security.SessionUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TripControllerTest {

    @Test
    void createsTripWithUserFromLoginSession() {
        TripService tripService = mock(TripService.class);
        TripDTO stored = TripDTO.builder().tripId(42L).userId(7L).destinationName("서울").build();
        when(tripService.create(any(TripDTO.class))).thenReturn(42L);
        when(tripService.get(42L)).thenReturn(stored);
        TripController controller = new TripController(tripService, new SessionUserResolver());

        TripDTO requestBody = TripDTO.builder()
                .userId(999L)
                .destinationName("서울")
                .startDate(LocalDate.of(2026, 8, 4))
                .endDate(LocalDate.of(2026, 8, 6))
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        request.setSession(session);

        var response = controller.create(requestBody, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/trips/42");
        assertThat(requestBody.getUserId()).isEqualTo(7L);
        verify(tripService).create(requestBody);
    }
}
