package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.service.support.TripCreateValidator;
import org.example.all_my_trip_project.domain.trip.service.support.TripCreationFactory;
import org.example.all_my_trip_project.domain.trip.type.CompanionType;
import org.example.all_my_trip_project.domain.user.dao.UserDAO;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;
import org.example.all_my_trip_project.domain.user.type.UserStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
    private UserDAO userDAO;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripDAO,
                tripDayDAO,
                userDAO,
                new TripCreateValidator(),
                new TripCreationFactory()
        );
        lenient().when(userDAO.findById(42L)).thenReturn(Optional.of(
                UserDTO.builder().userId(42L).status(UserStatus.ACTIVE.name()).build()
        ));
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
    void createRejectsUnknownUserBeforeSaving() {
        when(userDAO.findById(43L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.create(43L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoTripSaved();
    }

    @Test
    void createRejectsSuspendedUserBeforeSaving() {
        when(userDAO.findById(43L)).thenReturn(Optional.of(
                UserDTO.builder().userId(43L).status(UserStatus.SUSPENDED.name()).build()
        ));

        assertThatThrownBy(() -> tripService.create(43L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verifyNoTripSaved();
    }

    @Test
    void createRejectsWithdrawnUserBeforeSaving() {
        when(userDAO.findById(43L)).thenReturn(Optional.of(
                UserDTO.builder().userId(43L).status(UserStatus.WITHDRAWN.name()).build()
        ));

        assertThatThrownBy(() -> tripService.create(43L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

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
