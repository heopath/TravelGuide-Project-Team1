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
import java.time.LocalDate;

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

    @Test
    void createRejectsDateOutsideTripPeriod() {
        TripDTO trip = trip();
        TripDayDTO day = TripDayDTO.builder().tripId(10L).dayNumber(2)
                .tripDate(LocalDate.of(2026, 8, 13)).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> tripDayService.create(42L, day))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tripDate는 여행 시작일과 종료일 사이여야 합니다.");
        verify(tripDayDAO, never()).insert(day);
    }

    @Test
    void createRejectsDuplicateDayNumberOrDate() {
        TripDayDTO existing = TripDayDTO.builder().tripDayId(20L).tripId(10L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 10)).build();
        TripDayDTO duplicate = TripDayDTO.builder().tripId(10L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 11)).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip()));
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> tripDayService.create(42L, duplicate))
                .hasMessage("dayNumber와 tripDate는 여행 안에서 중복될 수 없습니다.");
    }

    @Test
    void createRejectsMoreThanThirtyDays() {
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip()));
        when(tripDayDAO.findByTripId(10L)).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, 30)
                        .mapToObj(number -> TripDayDTO.builder().tripDayId((long) number).build())
                        .toList());
        TripDayDTO day = TripDayDTO.builder().tripId(10L).dayNumber(2)
                .tripDate(LocalDate.of(2026, 8, 11)).build();

        assertThatThrownBy(() -> tripDayService.create(42L, day))
                .hasMessage("여행 일자는 최대 30개까지 등록할 수 있습니다.");
    }

    private TripDTO trip() {
        return TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 12)).build();
    }
}
