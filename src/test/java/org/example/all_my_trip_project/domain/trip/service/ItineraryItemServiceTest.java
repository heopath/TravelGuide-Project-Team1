package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소유권 검사는 공유 {@link TripOwnershipGuard}가 담당하므로 생성자로 명시적으로 조립한다.
 * {@code @InjectMocks}는 생성자 시그니처가 바뀌어도 컴파일 오류 없이 null을 주입해 조용히 깨지므로 쓰지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ItineraryItemServiceTest {

    @Mock
    private ItineraryItemDAO itemDAO;
    @Mock
    private TripDayDAO tripDayDAO;
    @Mock
    private TripDAO tripDAO;

    private ItineraryItemService itineraryItemService;

    @BeforeEach
    void setUp() {
        TripOwnershipGuard ownershipGuard = new TripOwnershipGuard(tripDAO, tripDayDAO, itemDAO);
        itineraryItemService = new ItineraryItemService(itemDAO, ownershipGuard, new ItineraryItemValidator());
    }

    @Test
    void createAddsItemToOwnedTripDayAtNextSortOrder() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder()
                .itineraryItemId(30L)
                .tripDayId(20L)
                .placeId(100L)
                .title("해운대 산책")
                .build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countByTripDayId(20L)).thenReturn(3);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 100L)).thenReturn(false);
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(3);

        assertThat(itineraryItemService.create(42L, item)).isEqualTo(30L);

        assertThat(item.getSortOrder()).isEqualTo(3);
        verify(itemDAO).insert(item);
    }

    @Test
    void createRejectsWhenDayAlreadyHasMaximumItems() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).title("해운대 산책").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countByTripDayId(20L)).thenReturn(100);

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_ITEM_LIMIT_EXCEEDED);

        verify(itemDAO, never()).insert(item);
    }

    @Test
    void createUsesMaxSortOrderPlusOneAfterMiddleItemWasDeleted() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder()
                .itineraryItemId(31L).tripDayId(20L).placeId(101L).title("새 장소").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countByTripDayId(20L)).thenReturn(2);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 101L)).thenReturn(false);
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(3);

        itineraryItemService.create(42L, item);

        assertThat(item.getSortOrder()).isEqualTo(3);
        verify(itemDAO).insert(item);
    }

    @Test
    void createRejectsPlaceAlreadyStoredInSameDay() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).placeId(100L).title("중복 장소").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_PLACE_ALREADY_ADDED);

        verify(itemDAO, never()).insert(item);
    }

    @Test
    void createTreatsConcurrentDuplicatePlaceInsertAsAlreadyAdded() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).placeId(100L).title("동시 요청 장소").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 100L)).thenReturn(false, true);
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(1);
        doThrow(new DataIntegrityViolationException("duplicate place"))
                .when(itemDAO).insert(item);

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_PLACE_ALREADY_ADDED);
    }

    @Test
    void updateKeepsExistingSortOrderRegardlessOfIncomingValue() {
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 제목").sortOrder(4).build();
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO update = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("변경된 제목").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.findById(30L)).thenReturn(Optional.of(existing));
        when(itemDAO.update(update)).thenReturn(1);

        itineraryItemService.update(42L, update);

        assertThat(update.getSortOrder()).isEqualTo(4);
    }

    @Test
    void createRejectsAnotherUsersTripDay() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(99L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).title("해운대 산책").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_NOT_FOUND);

        verify(itemDAO, never()).insert(item);
    }
}
