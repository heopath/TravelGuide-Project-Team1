package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryTimeBatchUpdateRequest;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
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
        itineraryItemService = new ItineraryItemService(
                itemDAO, ownershipGuard, new ItineraryItemValidator(), new ItineraryItemTimeConflictValidator());
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
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(3);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 100L)).thenReturn(false);
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(3);
        doAnswer(invocation -> {
            ItineraryItemDTO inserted = invocation.getArgument(0);
            assertThat(inserted.getItineraryItemId()).isNull();
            inserted.setItineraryItemId(901L);
            return null;
        }).when(itemDAO).insert(item);

        assertThat(itineraryItemService.create(42L, item)).isEqualTo(901L);

        assertThat(item.getSortOrder()).isEqualTo(3);
        verify(itemDAO).insert(item);
    }

    @Test
    void createAllowsFifthItem() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(10L).tripId(11L).build();
        TripDTO trip = TripDTO.builder().tripId(11L).userId(42L).build();
        ItineraryItemDTO item = itineraryItem();
        when(tripDayDAO.findById(10L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(11L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(10L)).thenReturn(4);

        itineraryItemService.create(42L, item);

        verify(itemDAO).insert(item);
    }

    @Test
    void createRejectsWhenDayAlreadyHasMaximumItems() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder().tripDayId(20L).title("해운대 산책").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(5);

        assertThatThrownBy(() -> itineraryItemService.create(42L, item))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_ITEM_LIMIT_EXCEEDED);

        verify(itemDAO, never()).insert(item);
    }

    @Test
    void createAllowsBookingItemAfterDayAlreadyHasFivePlaces() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO booking = ItineraryItemDTO.builder()
                .tripDayId(20L)
                .itemType("ACCOMMODATION")
                .title("예약 숙소")
                .build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(7);

        itineraryItemService.create(42L, booking);

        verify(itemDAO, never()).countPlaceItemsByTripDayId(20L);
        verify(itemDAO).insert(booking);
        assertThat(booking.getSortOrder()).isEqualTo(7);
    }

    @Test
    void createUsesMaxSortOrderPlusOneAfterMiddleItemWasDeleted() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO item = ItineraryItemDTO.builder()
                .itineraryItemId(31L).tripDayId(20L).placeId(101L).title("새 장소").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(2);
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
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
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
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
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
    void createRejectsAiRecommendationWhenTimeOverlapsExistingItem() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO candidate = ItineraryItemDTO.builder()
                .tripDayId(20L).placeId(101L).title("AI 추천 카페")
                .startTime(LocalTime.of(11, 0)).source("AI").build();
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .tripDayId(20L).title("기존 점심 일정")
                .startTime(LocalTime.of(10, 0)).build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 101L)).thenReturn(false);
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> itineraryItemService.create(42L, candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_TIME_CONFLICT);

        verify(itemDAO, never()).insert(candidate);
    }

    @Test
    void createAllowsAiRecommendationWhenItStartsAtExistingItemEndTime() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO candidate = ItineraryItemDTO.builder()
                .itineraryItemId(101L).tripDayId(20L).placeId(102L).title("AI 추천 카페")
                .startTime(LocalTime.of(12, 0)).source("AI").build();
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .tripDayId(20L).title("기존 점심 일정")
                .startTime(LocalTime.of(10, 0)).build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 102L)).thenReturn(false);
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(existing));
        when(itemDAO.nextSortOrderByTripDayId(20L)).thenReturn(1);
        doAnswer(invocation -> {
            ItineraryItemDTO inserted = invocation.getArgument(0);
            assertThat(inserted.getItineraryItemId()).isNull();
            inserted.setItineraryItemId(902L);
            return null;
        }).when(itemDAO).insert(candidate);

        assertThat(itineraryItemService.create(42L, candidate)).isEqualTo(902L);

        verify(itemDAO).insert(candidate);
    }

    @Test
    void createRejectsAiRecommendationEvenWhenRequestContainsExistingItemId() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO candidate = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).placeId(105L).title("ID 위조 AI 추천")
                .startTime(LocalTime.of(11, 0)).source("AI").build();
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 점심 일정")
                .startTime(LocalTime.of(10, 0)).build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 105L)).thenReturn(false);
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> itineraryItemService.create(42L, candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_TIME_CONFLICT);

        assertThat(candidate.getItineraryItemId()).isNull();
        verify(itemDAO, never()).insert(candidate);
    }

    @Test
    void createRejectsLateNightAiRecommendationBeforeItCanCrossMidnight() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO candidate = ItineraryItemDTO.builder()
                .tripDayId(20L).placeId(103L).title("AI 추천 야시장")
                .startTime(LocalTime.of(22, 30)).source("AI").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(1);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 103L)).thenReturn(false);

        assertThatThrownBy(() -> itineraryItemService.create(42L, candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_TIME_CONFLICT);

        verify(itemDAO, never()).insert(candidate);
    }

    @Test
    void createRejectsAiRecommendationAtMidnightBoundaryEvenWithoutExistingItems() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO candidate = ItineraryItemDTO.builder()
                .tripDayId(20L).placeId(104L).title("AI 추천 심야 카페")
                .startTime(LocalTime.of(22, 0)).source("AI").build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.countPlaceItemsByTripDayId(20L)).thenReturn(0);
        when(itemDAO.existsByTripDayIdAndPlaceId(20L, 104L)).thenReturn(false);

        assertThatThrownBy(() -> itineraryItemService.create(42L, candidate))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_TIME_CONFLICT);

        verify(itemDAO, never()).insert(candidate);
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
    void updateKeepsThirtyMinuteTimeRangeForSubsequentRead() {
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 제목").sortOrder(4).build();
        ItineraryItemDTO update = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 제목")
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(13, 30)).build();
        prepareOwnedItemForUpdate(existing);
        when(itemDAO.update(update)).thenReturn(1);

        itineraryItemService.update(42L, update);

        assertThat(update.getStartTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(update.getEndTime()).isEqualTo(LocalTime.of(13, 30));
        verify(itemDAO).update(update);
    }

    @Test
    void updateKeepsThreeHourTimeRangeForSubsequentRead() {
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 제목").sortOrder(4).build();
        ItineraryItemDTO update = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("기존 제목")
                .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(17, 0)).build();
        prepareOwnedItemForUpdate(existing);
        when(itemDAO.update(update)).thenReturn(1);

        itineraryItemService.update(42L, update);

        assertThat(update.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(update.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        verify(itemDAO).update(update);
    }

    @Test
    void updateRejectsTimeThatOverlapsAnotherItemOnSameDay() {
        ItineraryItemDTO existing = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("수정할 일정").sortOrder(4)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0)).build();
        ItineraryItemDTO otherItem = ItineraryItemDTO.builder()
                .itineraryItemId(31L).tripDayId(20L).title("기존 점심 일정")
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).build();
        ItineraryItemDTO update = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("수정할 일정")
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).build();
        prepareOwnedItemForUpdate(existing);
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(existing, otherItem));

        assertThatThrownBy(() -> itineraryItemService.update(42L, update))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITINERARY_TIME_CONFLICT);

        verify(itemDAO, never()).update(update);
    }

    @Test
    void validatorRejectsAnOvernightTimeRange() {
        ItineraryItemDTO update = ItineraryItemDTO.builder()
                .title("심야 일정")
                .startTime(LocalTime.of(23, 30)).endTime(LocalTime.of(1, 30)).build();

        assertThatThrownBy(() -> new ItineraryItemValidator().validate(update))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("일정 종료 시각은 시작 시각보다 빠를 수 없습니다.");
    }

    @Test
    void updateScheduleTimesUpdatesEveryItemInOwnedDay() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO first = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("첫 장소").sortOrder(0).build();
        ItineraryItemDTO second = ItineraryItemDTO.builder()
                .itineraryItemId(31L).tripDayId(20L).title("둘째 장소").sortOrder(1).build();
        ItineraryTimeBatchUpdateRequest request = new ItineraryTimeBatchUpdateRequest(List.of(
                new ItineraryTimeBatchUpdateRequest.ItemTime(
                        30L, LocalTime.of(9, 0), LocalTime.of(10, 30)),
                new ItineraryTimeBatchUpdateRequest.ItemTime(
                        31L, LocalTime.of(11, 0), LocalTime.of(12, 0))));
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(first, second));
        when(itemDAO.update(first)).thenReturn(1);
        when(itemDAO.update(second)).thenReturn(1);

        List<ItineraryItemDTO> updated = itineraryItemService.updateScheduleTimes(42L, 20L, request);

        assertThat(updated).containsExactly(first, second);
        assertThat(first.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(first.getEndTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(second.getStartTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(second.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        verify(itemDAO).update(first);
        verify(itemDAO).update(second);
    }

    @Test
    void updateScheduleTimesRejectsPartialDayBeforeUpdatingAnything() {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        ItineraryItemDTO first = ItineraryItemDTO.builder()
                .itineraryItemId(30L).tripDayId(20L).title("첫 장소").sortOrder(0).build();
        ItineraryItemDTO second = ItineraryItemDTO.builder()
                .itineraryItemId(31L).tripDayId(20L).title("둘째 장소").sortOrder(1).build();
        ItineraryTimeBatchUpdateRequest partialRequest = new ItineraryTimeBatchUpdateRequest(List.of(
                new ItineraryTimeBatchUpdateRequest.ItemTime(
                        30L, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.findByTripDayId(20L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> itineraryItemService.updateScheduleTimes(42L, 20L, partialRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 일자의 모든 일정 시간을 보내야 합니다.");

        verify(itemDAO, never()).update(first);
        verify(itemDAO, never()).update(second);
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

    private ItineraryItemDTO itineraryItem() {
        return ItineraryItemDTO.builder()
                .tripDayId(10L)
                .title("해운대해수욕장")
                .build();
    }

    private void prepareOwnedItemForUpdate(ItineraryItemDTO existing) {
        TripDayDTO day = TripDayDTO.builder().tripDayId(20L).tripId(10L).build();
        TripDTO trip = TripDTO.builder().tripId(10L).userId(42L).build();
        when(tripDayDAO.findById(20L)).thenReturn(Optional.of(day));
        when(tripDAO.findById(10L)).thenReturn(Optional.of(trip));
        when(itemDAO.findById(30L)).thenReturn(Optional.of(existing));
    }
}
