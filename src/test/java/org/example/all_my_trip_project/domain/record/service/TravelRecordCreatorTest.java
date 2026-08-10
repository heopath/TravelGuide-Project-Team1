package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.CreateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordRepository;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.domain.trip.type.TripStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * record 도메인이 trip 도메인의 Repository를 직접 참조하지 않고 공개 계약({@link TripService#get})만으로
 * 완료 여행·소유권을 확인한다는 것이 이 클래스의 핵심 규칙이므로, 그 경계를 직접 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelRecordCreatorTest {

    @Mock
    private TravelRecordRepository travelRecordRepository;
    @Mock
    private TripService tripService;

    private TravelRecordCreator creator;

    @BeforeEach
    void setUp() {
        creator = new TravelRecordCreator(travelRecordRepository, tripService);
    }

    @Test
    void rejectsTripThatIsNotCompleted() {
        when(tripService.get(42L, 10L)).thenReturn(trip(TripStatus.CONFIRMED));

        assertThatThrownBy(() -> creator.create(42L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRIP_NOT_COMPLETED);

        verify(travelRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWhenTripAlreadyHasARecord() {
        when(tripService.get(42L, 10L)).thenReturn(trip(TripStatus.COMPLETED));
        when(travelRecordRepository.existsByTripIdAndDeletedAtIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> creator.create(42L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_ALREADY_EXISTS);

        verify(travelRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void savesTrimmedRecordForCompletedTripWithoutExistingRecord() {
        when(tripService.get(42L, 10L)).thenReturn(trip(TripStatus.COMPLETED));
        when(travelRecordRepository.existsByTripIdAndDeletedAtIsNull(10L)).thenReturn(false);
        when(travelRecordRepository.save(org.mockito.ArgumentMatchers.any(TravelRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecordEntity saved = creator.create(42L, new CreateTravelRecordRequest(
                10L, "  제주 여행 기록  ", "본문", (short) 5, RecordVisibility.PUBLIC));

        ArgumentCaptor<TravelRecordEntity> captor = ArgumentCaptor.forClass(TravelRecordEntity.class);
        verify(travelRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("제주 여행 기록");
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getTripId()).isEqualTo(10L);
        assertThat(captor.getValue().isPublic()).isTrue();
        assertThat(saved).isSameAs(captor.getValue());
    }

    private CreateTravelRecordRequest request() {
        return new CreateTravelRecordRequest(10L, "제목", "본문", (short) 5, RecordVisibility.PRIVATE);
    }

    private TripDTO trip(TripStatus status) {
        return TripDTO.builder().tripId(10L).userId(42L).status(status.name()).build();
    }
}
