package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.type.CompanionType;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripDAO tripDAO;
    @Mock
    private TripDayDAO tripDayDAO;
    @Mock
    private ItineraryItemDAO itineraryItemDAO;
    @Mock
    private ActiveMemberGuard activeMemberGuard;
    @Mock
    private TripDayService tripDayService;
    @Mock
    private ItineraryItemService itineraryItemService;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripDAO,
                tripDayDAO,
                activeMemberGuard,
                new TripOwnershipGuard(tripDAO, tripDayDAO, itineraryItemDAO),
                tripDayService,
                itineraryItemService,
                new TripCreateValidator(),
                new TripCreationFactory()
        );
    }

    @Test
    void createStoresTripAndEveryDayTogether() {
        TripCreateRequest request = request(
                null,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                CompanionType.COUPLE,
                2
        );
        prepareSuccessfulInsert();

        TripCreateResult result = tripService.create(42L, request);

        assertThat(result.tripId()).isEqualTo(10L);
        assertThat(result.createdDayCount()).isEqualTo(3);

        ArgumentCaptor<TripDTO> tripCaptor = ArgumentCaptor.forClass(TripDTO.class);
        verify(tripDAO).insert(tripCaptor.capture());
        assertThat(tripCaptor.getValue().getTitle()).isEqualTo("부산 여행");
        assertThat(tripCaptor.getValue().getUserId()).isEqualTo(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TripDayDTO>> daysCaptor = ArgumentCaptor.forClass(List.class);
        verify(tripDayDAO).insertAll(daysCaptor.capture());
        assertThat(daysCaptor.getValue()).extracting(TripDayDTO::getTripDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 8, 12));
        assertThat(daysCaptor.getValue()).extracting(TripDayDTO::getDayNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    void createAllowsThirtyDayTrip() {
        TripCreateRequest request = request(
                "여름 휴가",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 30),
                CompanionType.FAMILY,
                4
        );
        prepareSuccessfulInsert();

        TripCreateResult result = tripService.create(42L, request);

        assertThat(result.createdDayCount()).isEqualTo(30);
        verify(tripDayDAO).insertAll(anyList());
    }

    @Test
    void createRejectsThirtyOneDayTripBeforeSaving() {
        TripCreateRequest request = request(
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                CompanionType.COUPLE,
                2
        );

        assertThatThrownBy(() -> tripService.create(42L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TRIP_PERIOD);

        verify(tripDAO, never()).insert(any(TripDTO.class));
        verify(tripDayDAO, never()).insertAll(anyList());
    }

    @Test
    void createRejectsSoloWithMoreThanOneCompanion() {
        TripCreateRequest request = request(
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                CompanionType.SOLO,
                2
        );

        assertThatThrownBy(() -> tripService.create(42L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_COMPANION_COUNT);

        verify(tripDAO, never()).insert(any(TripDTO.class));
    }

    @Test
    void createPropagatesBatchInsertFailure() {
        TripCreateRequest request = request(
                null,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                CompanionType.COUPLE,
                2
        );
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(10L);
            return 1;
        }).when(tripDAO).insert(any(TripDTO.class));
        when(tripDayDAO.insertAll(anyList())).thenThrow(new IllegalStateException("DAY 저장 실패"));

        assertThatThrownBy(() -> tripService.create(42L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DAY 저장 실패");
    }

    @Test
    void createRejectsNonPositiveUserIdBeforeCheckingMembership() {
        assertThatThrownBy(() -> tripService.create(0L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(activeMemberGuard, never()).requireActiveMember(any());
        verifyNoTripSaved();
    }

    @Test
    void createPropagatesActiveMemberGuardRejectionBeforeSaving() {
        // 회원 존재·정지·탈퇴 판단은 MemberService.requireActiveMember()의 책임이다(MemberServiceTest 참고).
        // TripService는 ActiveMemberGuard의 결과를 그대로 전파하는지만 검증한다.
        doThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED))
                .when(activeMemberGuard).requireActiveMember(43L);

        assertThatThrownBy(() -> tripService.create(43L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verifyNoTripSaved();
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
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_NOT_FOUND);
    }

    private void prepareSuccessfulInsert() {
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(10L);
            return 1;
        }).when(tripDAO).insert(any(TripDTO.class));
        doAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size())
                .when(tripDayDAO).insertAll(anyList());
    }

    private TripCreateRequest validRequest() {
        return request(
                null,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                CompanionType.COUPLE,
                2
        );
    }

    private void verifyNoTripSaved() {
        verify(tripDAO, never()).insert(any(TripDTO.class));
        verify(tripDayDAO, never()).insertAll(anyList());
    }

    private TripCreateRequest request(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            CompanionType companionType,
            int companionCount
    ) {
        return new TripCreateRequest(
                title,
                "부산",
                startDate,
                endDate,
                companionType,
                companionCount,
                BigDecimal.valueOf(300_000)
        );
    }

    @Test
    void updateDelegatesPeriodConflictCheckAndReconciliationToTripDayService() {
        TripDTO saved = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 10)).endDate(LocalDate.of(2026, 8, 12)).build();
        TripDTO update = TripDTO.builder().tripId(10L).userId(42L)
                .startDate(LocalDate.of(2026, 8, 11)).endDate(LocalDate.of(2026, 8, 12)).build();
        when(tripDAO.findById(10L)).thenReturn(Optional.of(saved));
        when(tripDAO.update(update)).thenReturn(1);

        tripService.update(42L, update);

        InOrder order = inOrder(tripDayService, tripDAO);
        order.verify(tripDayService).ensureNoPeriodConflict(saved, update);
        order.verify(tripDAO).update(update);
        order.verify(tripDayService).reconcilePeriod(saved, update);
    }
}
