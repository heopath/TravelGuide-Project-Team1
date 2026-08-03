package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryItemServiceTest {

    @Mock
    private ItineraryItemDAO itemDAO;
    @Mock
    private TripDayDAO tripDayDAO;
    @Mock
    private TripDAO tripDAO;

    @InjectMocks
    private ItineraryItemService itineraryItemService;

    @Test
    void createAddsItemToOwnedTripDay() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder()
                .itineraryItemId(30L)
                .tripDayId(20L)
                .placeId(100L)
                .build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThat(itineraryItemService.create(42L, item)).isEqualTo(30L);

        verify(itemDAO).insert(item);
    }

    @Test
    void createRejectsAnotherUsersTripDay() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(99L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행 일자를 찾을 수 없습니다.");

        verify(itemDAO, never()).insert(item);
    }
}
