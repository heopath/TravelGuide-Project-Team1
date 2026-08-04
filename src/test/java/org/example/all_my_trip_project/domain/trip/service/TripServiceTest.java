package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripDAO tripDAO;
    @Mock
    private TripDayDAO tripDayDAO;

    @InjectMocks
    private TripService tripService;

    @Test
    void createWithDaysStoresTripAndEveryDayTogether() {
        TripDTO trip = TripDTO.builder()
                .userId(999L)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 12))
                .build();
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(10L);
            return 1;
        }).when(tripDAO).insert(trip);
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        TripCreateResult result = tripService.createWithDays(42L, trip);

        assertThat(result.trip().getUserId()).isEqualTo(42L);
        assertThat(result.days()).extracting(TripDayDTO::getTripDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 8, 12));
        ArgumentCaptor<TripDayDTO> dayCaptor = ArgumentCaptor.forClass(TripDayDTO.class);
        verify(tripDayDAO, times(3)).insert(dayCaptor.capture());
        assertThat(dayCaptor.getAllValues()).extracting(TripDayDTO::getDayNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    void createWithDaysAllowsThirtyDayTrip() {
        TripDTO trip = TripDTO.builder()
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 30))
                .build();
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(10L);
            return 1;
        }).when(tripDAO).insert(trip);
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        TripCreateResult result = tripService.createWithDays(42L, trip);

        assertThat(result.days()).hasSize(30);
        verify(tripDAO).insert(trip);
        verify(tripDayDAO, times(30)).insert(any(TripDayDTO.class));
    }

    @Test
    void createWithDaysRejectsThirtyOneDayTripBeforeSaving() {
        TripDTO trip = TripDTO.builder()
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .build();

        assertThatThrownBy(() -> tripService.createWithDays(42L, trip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행 기간은 최대 30일까지 설정할 수 있습니다.");

        verify(tripDAO, never()).insert(any(TripDTO.class));
        verify(tripDayDAO, never()).insert(any(TripDayDTO.class));
    }

    @Test
    void createWithDaysPropagatesDayInsertFailureForTransactionRollback() {
        TripDTO trip = TripDTO.builder()
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 12))
                .build();
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(10L);
            return 1;
        }).when(tripDAO).insert(trip);
        AtomicInteger inserts = new AtomicInteger();
        doAnswer(invocation -> {
            if (inserts.incrementAndGet() == 2) {
                throw new IllegalStateException("DAY 저장 실패");
            }
            return 1;
        }).when(tripDayDAO).insert(org.mockito.ArgumentMatchers.any(TripDayDTO.class));

        assertThatThrownBy(() -> tripService.createWithDays(42L, trip))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DAY 저장 실패");

        verify(tripDayDAO, times(2)).insert(org.mockito.ArgumentMatchers.any(TripDayDTO.class));
    }

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

    @Test
    void updateSynchronizesExistingDaysWhenPeriodChanges() {
        TripDTO saved = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10)).endDate(LocalDate.of(2026, 8, 12)).build();
        TripDTO update = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 11)).endDate(LocalDate.of(2026, 8, 12)).build();
        TripDayDTO first = TripDayDTO.builder().tripDayId(21L).tripId(10L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 10)).title("첫날").build();
        TripDayDTO second = TripDayDTO.builder().tripDayId(22L).tripId(10L).dayNumber(2)
                .tripDate(LocalDate.of(2026, 8, 11)).title("둘째날").build();
        TripDayDTO removed = TripDayDTO.builder().tripDayId(23L).tripId(10L).dayNumber(3)
                .tripDate(LocalDate.of(2026, 8, 12)).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(saved));
        when(tripDAO.update(update)).thenReturn(1);
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of(first, second, removed));

        tripService.update(42L, update);

        verify(tripDayDAO).moveOutOfDateRange(10L);
        assertThat(first.getTripDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(second.getTripDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        verify(tripDayDAO).delete(23L);
    }
}
