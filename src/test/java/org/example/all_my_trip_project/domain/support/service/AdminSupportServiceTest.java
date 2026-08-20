package org.example.all_my_trip_project.domain.support.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.support.dao.AdminSupportDAO;
import org.example.all_my_trip_project.domain.support.dto.AdminSupportInquiryDetail;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSupportServiceTest {
    @Mock private AdminSupportDAO dao;
    /* 감사 기록은 이 테스트의 관심사가 아니지만 @InjectMocks가 채우려면 선언돼 있어야 한다. */
    @Mock private AdminAuditService adminAuditService;
    /* 알림도 마찬가지다. 곁다리라 이 테스트가 보려는 것과 무관하다. */
    @Mock private org.example.all_my_trip_project.domain.notification.service.NotificationService notificationService;
    @InjectMocks private AdminSupportService service;

    @Test
    void replyStoresAdministratorAndMarksInquiryAnswered() {
        SupportInquiryDTO open = inquiry("OPEN");
        SupportInquiryDTO answered = inquiry("ANSWERED");
        when(dao.findInquiry(11L)).thenReturn(Optional.of(open), Optional.of(answered));
        when(dao.insertReply(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(dao.updateStatus(11L, "ANSWERED")).thenReturn(1);
        when(dao.findReplies(11L)).thenReturn(List.of(
                SupportReplyDTO.builder().supportReplyId(20L).supportInquiryId(11L).build()));

        AdminSupportInquiryDetail result = service.reply(
                9L, 11L, new SupportReplyRequest("  확인 후 답변드립니다.  "));

        ArgumentCaptor<SupportReplyDTO> reply = ArgumentCaptor.forClass(SupportReplyDTO.class);
        verify(dao).insertReply(reply.capture());
        assertThat(reply.getValue().getAdminUserId()).isEqualTo(9L);
        assertThat(reply.getValue().getContent()).isEqualTo("확인 후 답변드립니다.");
        verify(dao).updateStatus(11L, "ANSWERED");
        assertThat(result.inquiry().getStatus()).isEqualTo("ANSWERED");
    }

    private SupportInquiryDTO inquiry(String status) {
        return SupportInquiryDTO.builder()
                .supportInquiryId(11L)
                .userId(7L)
                .title("문의")
                .content("문의 내용")
                .status(status)
                .build();
    }
}
