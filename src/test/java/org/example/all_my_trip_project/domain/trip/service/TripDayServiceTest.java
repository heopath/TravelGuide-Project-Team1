package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripDayServiceTest {

    @Mock
    private TripDayDAO tripDayDAO;
    @Mock
    private TripDAO tripDAO;

    @InjectMocks
    private TripDayService tripDayService;

    @Test
    void getByTripReturnsDaysForOwner() {
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of(day));

        assertThat(tripDayService.getByTrip(42L, 10L)).containsExactly(day);
    }

    @Test
    void getByTripRejectsAnotherUsersTrip() {
        TripDTO trip = TripDTO.builder().tripId(10L).userId(99L).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripDayService.getByTrip(42L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행을 찾을 수 없습니다.");

        verify(tripDayDAO, never()).findByTripId(10L);
    }
}
