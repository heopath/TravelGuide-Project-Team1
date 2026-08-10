package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordImageRepository;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordRepository;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PUBLIC 기록은 누구나, PRIVATE 기록은 소유자만 볼 수 있다는 규칙과, 존재하지 않거나 볼 수 없는 기록을
 * 구분하지 않고 동일하게 404로 통일한다는 규칙(trip-service-structure.md의 소유권 검사 정책과 동일한
 * "존재 여부를 노출하지 않는다" 원칙)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelRecordReaderTest {

    @Mock
    private TravelRecordRepository travelRecordRepository;
    @Mock
    private TravelRecordImageRepository travelRecordImageRepository;

    private TravelRecordReader reader;

    @BeforeEach
    void setUp() {
        reader = new TravelRecordReader(travelRecordRepository, travelRecordImageRepository,
                new TravelRecordResponseMapper());
    }

    @Test
    void anyoneCanReadAPublicRecordIncludingAnonymousViewers() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PUBLIC)));

        assertThat(reader.findAccessible(null, 1L).getTravelRecordId()).isEqualTo(1L);
        assertThat(reader.findAccessible(999L, 1L).getTravelRecordId()).isEqualTo(1L);
    }

    @Test
    void onlyOwnerCanReadAPrivateRecord() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PRIVATE)));

        assertThat(reader.findAccessible(7L, 1L).getTravelRecordId()).isEqualTo(1L);
    }

    @Test
    void rejectsNonOwnerViewerOfAPrivateRecord() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PRIVATE)));

        assertThatThrownBy(() -> reader.findAccessible(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND);
    }

    @Test
    void rejectsAnonymousViewerOfAPrivateRecord() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PRIVATE)));

        assertThatThrownBy(() -> reader.findAccessible(null, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND);
    }

    @Test
    void missingOrDeletedRecordIsReportedAsNotFound() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.findAccessible(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND);
    }

    @Test
    void findOwnedRejectsAnyoneButTheOwner() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PUBLIC)));

        assertThatThrownBy(() -> reader.findOwned(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND);
    }

    @Test
    void getAccessViewExposesOwnerAndVisibilityForCrossDomainUse() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PUBLIC)));

        TravelRecordAccessView view = reader.getAccessView(7L, 1L);

        assertThat(view.travelRecordId()).isEqualTo(1L);
        assertThat(view.tripId()).isEqualTo(10L);
        assertThat(view.ownerUserId()).isEqualTo(7L);
        assertThat(view.visibility()).isEqualTo(RecordVisibility.PUBLIC);
    }

    @Test
    void getReturnsResponseWithImagesInSortOrder() {
        when(travelRecordRepository.findByTravelRecordIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(record(RecordVisibility.PUBLIC)));
        when(travelRecordImageRepository.findByTravelRecordIdOrderBySortOrderAsc(1L))
                .thenReturn(List.of());

        assertThat(reader.get(7L, 1L).images()).isEmpty();
    }

    private TravelRecordEntity record(RecordVisibility visibility) {
        TravelRecordEntity entity = TravelRecordEntity.create(10L, 7L, "제목", "본문", (short) 5, visibility);
        setTravelRecordId(entity, 1L);
        return entity;
    }

    private void setTravelRecordId(TravelRecordEntity entity, Long id) {
        try {
            var field = TravelRecordEntity.class.getDeclaredField("travelRecordId");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
