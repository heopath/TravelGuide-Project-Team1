package org.example.all_my_trip_project.domain.social.service;

import org.example.all_my_trip_project.domain.social.dto.ProcessReportRequest;
import org.example.all_my_trip_project.domain.social.dto.ReportRecordRequest;
import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.example.all_my_trip_project.domain.social.type.ReportReason;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelRecordReportServiceTest {

    @Mock
    private ActiveMemberGuard activeMemberGuard;
    @Mock
    private ReportValidator validator;
    @Mock
    private ReportCreator creator;
    @Mock
    private ReportReader reader;
    @Mock
    private ReportProcessor processor;

    private TravelRecordReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new TravelRecordReportService(activeMemberGuard, validator, creator, reader, processor);
    }

    @Test
    void reportChecksActiveMembershipBeforeCreating() {
        TravelRecordReportEntity created = report();
        when(creator.create(9L, 1L, request())).thenReturn(created);

        reportService.report(9L, 1L, request());

        InOrder order = inOrder(activeMemberGuard, creator);
        order.verify(activeMemberGuard).requireActiveMember(9L);
        order.verify(creator).create(9L, 1L, request());
    }

    @Test
    void reportPropagatesActiveMemberGuardRejectionWithoutCallingCreator() {
        // ActiveMemberGuard 구현체(MemberService#validateMember)가 이미 null·미존재 userId를
        // 전부 UNAUTHORIZED로 거르므로, report()는 그 검사를 따로 하지 않고 위임만 한다.
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED))
                .when(activeMemberGuard).requireActiveMember(null);

        assertThatThrownBy(() -> reportService.report(null, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(creator, never()).create(any(), any(), any());
    }

    @Test
    void getReportsRejectsAnonymousCaller() {
        assertThatThrownBy(() -> reportService.getReports(null, ReportStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(reader, never()).findByStatus(any());
    }

    @Test
    void processValidatesTargetStatusBeforeLoadingTheReport() {
        TravelRecordReportEntity target = report();
        when(reader.findById(30L)).thenReturn(target);
        ProcessReportRequest request = new ProcessReportRequest(ReportStatus.RESOLVED, "  조치 완료  ");

        reportService.process(5L, 30L, request);

        InOrder order = inOrder(validator, reader, processor);
        order.verify(validator).validateTargetStatus(ReportStatus.RESOLVED);
        order.verify(reader).findById(30L);
        order.verify(processor).process(target, 5L, ReportStatus.RESOLVED, "조치 완료");
    }

    @Test
    void processTrimsBlankResolutionNoteToNull() {
        TravelRecordReportEntity target = report();
        when(reader.findById(30L)).thenReturn(target);

        reportService.process(5L, 30L, new ProcessReportRequest(ReportStatus.REJECTED, "   "));

        verify(processor).process(target, 5L, ReportStatus.REJECTED, null);
    }

    @Test
    void processRejectsAnonymousAdmin() {
        assertThatThrownBy(() -> reportService.process(
                null, 30L, new ProcessReportRequest(ReportStatus.RESOLVED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(reader, never()).findById(any());
    }

    private ReportRecordRequest request() {
        return new ReportRecordRequest(ReportReason.OTHER, "상세 사유");
    }

    private TravelRecordReportEntity report() {
        return TravelRecordReportEntity.create(1L, 9L, ReportReason.OTHER, "상세 사유");
    }
}
