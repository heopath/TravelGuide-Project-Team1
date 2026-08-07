package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TripOwnershipGuard}·{@link TripDayValidator} 등 협력 객체는 생성자로 명시적으로 조립한다.
 * {@code @InjectMocks}는 생성자 시그니처가 바뀌어도 컴파일 오류 없이 null을 주입해 조용히 깨지므로 쓰지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class TripDayServiceTest {

    @Mock
    private TripDayDAO tripDayDAO;
    @Mock
    private TripDAO tripDAO;
    @Mock
    private ItineraryItemDAO itineraryItemDAO;

    private TripDayService tripDayService;

    @BeforeEach
    void setUp() {
        TripOwnershipGuard ownershipGuard = new TripOwnershipGuard(tripDAO, tripDayDAO, itineraryItemDAO);
        tripDayService = new TripDayService(
                tripDayDAO,
                ownershipGuard,
                new TripDayValidator(),
                new TripPeriodChangeValidator(tripDayDAO),
                new TripDayReconciler(tripDayDAO)
        );
    }

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
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_NOT_FOUND);

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

    @Test
    void ensureNoPeriodConflictRejectsShrinkWithItemsOutsideNewPeriod() {
        TripDTO saved = trip();
        TripDTO requested = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10)).endDate(LocalDate.of(2026, 8, 11)).build();
        when(tripDayDAO.existsOutsidePeriodWithItineraryItems(
                10L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11)))
                .thenReturn(true);

        assertThatThrownBy(() -> tripDayService.ensureNoPeriodConflict(saved, requested))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_PERIOD_CONFLICT);
    }

    @Test
    void reconcilePeriodSynchronizesExistingDaysWhenPeriodChanges() {
        TripDTO saved = trip();
        TripDTO requested = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 11)).endDate(LocalDate.of(2026, 8, 12)).build();
        TripDayDTO first = TripDayDTO.builder().tripDayId(21L).tripId(10L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 10)).title("첫날").build();
        TripDayDTO second = TripDayDTO.builder().tripDayId(22L).tripId(10L).dayNumber(2)
                .tripDate(LocalDate.of(2026, 8, 11)).title("둘째날").build();
        TripDayDTO removed = TripDayDTO.builder().tripDayId(23L).tripId(10L).dayNumber(3)
                .tripDate(LocalDate.of(2026, 8, 12)).build();
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of(first, second, removed));

        tripDayService.reconcilePeriod(saved, requested);

        assertThat(second.getTripDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(removed.getTripDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        verify(tripDayDAO).delete(21L);
    }

    @Test
    void reconcilePeriodMovesRetainedDaysThroughTemporaryRangeToAvoidUniqueCollision() {
        // 8/12~8/14 -> 8/10~8/12로 당기면 겹치는 8/12(1일차)는 3일차가 돼야 하는데,
        // 아직 삭제되지 않은 기존 3일차(8/14)가 이미 3을 쓰고 있어 곧바로 UPDATE하면
        // UNIQUE(trip_id, day_number) 위반이 난다. 임시 번호를 거쳐가는지 검증한다.
        TripDTO saved = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 12)).endDate(LocalDate.of(2026, 8, 14)).build();
        TripDTO requested = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10)).endDate(LocalDate.of(2026, 8, 12)).build();
        TripDayDTO overlapping = TripDayDTO.builder().tripDayId(31L).tripId(10L).dayNumber(1)
                .tripDate(LocalDate.of(2026, 8, 12)).build();
        TripDayDTO droppedFirst = TripDayDTO.builder().tripDayId(32L).tripId(10L).dayNumber(2)
                .tripDate(LocalDate.of(2026, 8, 13)).build();
        TripDayDTO droppedSecond = TripDayDTO.builder().tripDayId(33L).tripId(10L).dayNumber(3)
                .tripDate(LocalDate.of(2026, 8, 14)).build();
        when(tripDayDAO.findByTripId(10L)).thenReturn(List.of(overlapping, droppedFirst, droppedSecond));

        // TripDayDTO는 가변 객체라 ArgumentCaptor는 마지막 상태만 보므로, 각 update() 호출 시점의
        // day_number를 스냅샷으로 즉시 기록한다.
        List<Integer> dayNumbersForOverlapping = new ArrayList<>();
        doAnswer(invocation -> {
            TripDayDTO argument = invocation.getArgument(0);
            if (argument.getTripDayId().equals(31L)) {
                dayNumbersForOverlapping.add(argument.getDayNumber());
            }
            return 1;
        }).when(tripDayDAO).update(ArgumentMatchers.any(TripDayDTO.class));

        tripDayService.reconcilePeriod(saved, requested);

        assertThat(dayNumbersForOverlapping).hasSize(2);
        assertThat(dayNumbersForOverlapping.get(0)).isGreaterThan(TripPolicy.MAX_TRIP_DAYS);
        assertThat(dayNumbersForOverlapping.get(1)).isEqualTo(3);
        verify(tripDayDAO).delete(32L);
        verify(tripDayDAO).delete(33L);
    }

    @Test
    void reconcilePeriodDoesNothingWhenPeriodUnchanged() {
        TripDTO saved = trip();
        TripDTO requested = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(saved.getStartDate()).endDate(saved.getEndDate()).build();

        tripDayService.reconcilePeriod(saved, requested);

        verify(tripDayDAO, never()).findByTripId(10L);
    }

    private TripDTO trip() {
        return TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 12)).build();
    }
}
