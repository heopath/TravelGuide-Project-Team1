package org.example.all_my_trip_project.domain.social.service;

import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.domain.record.service.TravelRecordAccessGuard;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.example.all_my_trip_project.domain.social.dto.ReportRecordRequest;
import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.repository.TravelRecordReportRepository;
import org.example.all_my_trip_project.domain.social.type.ReportReason;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 신고 대상 기록의 존재·가시성 확인은 record 도메인의 Repository/Entity를 직접 참조하지 않고
 * {@link TravelRecordAccessGuard} 계약 하나로만 이뤄진다는 도메인 경계를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReportCreatorTest {

    @Mock
    private TravelRecordReportRepository travelRecordReportRepository;
    @Mock
    private TravelRecordAccessGuard travelRecordAccessGuard;

    private ReportCreator creator;

    @BeforeEach
    void setUp() {
        creator = new ReportCreator(travelRecordReportRepository, travelRecordAccessGuard);
    }

    @Test
    void rejectsWhenReporterAlreadyHasAnOpenReportOnTheRecord() {
        when(travelRecordAccessGuard.requireAccessibleRecord(9L, 1L)).thenReturn(accessView());
        when(travelRecordReportRepository.existsByTravelRecordIdAndReporterUserIdAndStatusIn(eq(1L), eq(9L), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> creator.create(9L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ALREADY_PENDING);

        verify(travelRecordReportRepository, never()).save(any());
    }

    @Test
    void propagatesTheAccessGuardFailureWithoutSavingAnything() {
        when(travelRecordAccessGuard.requireAccessibleRecord(9L, 1L))
                .thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        assertThatThrownBy(() -> creator.create(9L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND);

        verify(travelRecordReportRepository, never())
                .existsByTravelRecordIdAndReporterUserIdAndStatusIn(any(), any(), any());
        verify(travelRecordReportRepository, never()).save(any());
    }

    @Test
    void savesAReportUsingTheRecordIdConfirmedByTheAccessGuardAndTrimsBlankDetailToNull() {
        when(travelRecordAccessGuard.requireAccessibleRecord(9L, 1L)).thenReturn(accessView());
        when(travelRecordReportRepository.existsByTravelRecordIdAndReporterUserIdAndStatusIn(eq(1L), eq(9L), anyList()))
                .thenReturn(false);
        when(travelRecordReportRepository.save(any(TravelRecordReportEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecordReportEntity saved =
                creator.create(9L, 1L, new ReportRecordRequest(ReportReason.SPAM, "   "));

        ArgumentCaptor<TravelRecordReportEntity> captor = ArgumentCaptor.forClass(TravelRecordReportEntity.class);
        verify(travelRecordReportRepository).save(captor.capture());
        assertThat(captor.getValue().getTravelRecordId()).isEqualTo(1L);
        assertThat(captor.getValue().getReporterUserId()).isEqualTo(9L);
        assertThat(captor.getValue().getDetail()).isNull();
        assertThat(saved).isSameAs(captor.getValue());
    }

    private TravelRecordAccessView accessView() {
        return new TravelRecordAccessView(1L, 10L, 42L, RecordVisibility.PUBLIC);
    }

    private ReportRecordRequest request() {
        return new ReportRecordRequest(ReportReason.INAPPROPRIATE, "무관한 광고성 링크입니다.");
    }
}
