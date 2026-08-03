package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripDAO tripDAO;

    @InjectMocks
    private TripService tripService;

    @Test
    void getReturnsTripOwnedByUser() {
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThat(tripService.get(42L, 10L)).isSameAs(trip);
    }

    @Test
    void getHidesTripOwnedByAnotherUser() {
        TripDTO trip = TripDTO.builder().tripId(10L).userId(99L).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.get(42L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행을 찾을 수 없습니다.");
    }
}
