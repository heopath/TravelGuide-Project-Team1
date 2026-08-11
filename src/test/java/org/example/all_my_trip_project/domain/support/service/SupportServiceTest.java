package org.example.all_my_trip_project.domain.support.service;

import org.example.all_my_trip_project.domain.support.dao.SupportDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryRequest;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {
    @Mock private SupportDAO supportDAO;
    @Mock private ActiveMemberGuard activeMemberGuard;
    @InjectMocks private SupportService service;

    @Test
    void createStoresInquiryForActiveMember() {
        when(supportDAO.insertInquiry(any(SupportInquiryDTO.class))).thenAnswer(invocation -> {
            SupportInquiryDTO inquiry = invocation.getArgument(0);
            inquiry.setSupportInquiryId(11L);
            return 1;
        });
        SupportInquiryDTO saved = inquiry(11L, 7L);
        when(supportDAO.findInquiry(11L)).thenReturn(Optional.of(saved));

        SupportInquiryDTO result = service.create(
                7L,
                new SupportInquiryRequest("AI_PLAN", "일정 문의", "AI 일정 수정 방법이 궁금합니다."));

        assertThat(result.getSupportInquiryId()).isEqualTo(11L);
        verify(activeMemberGuard).requireActiveMember(7L);
    }

    @Test
    void getMineReturnsPagedInquiries() {
        when(supportDAO.countMine(7L)).thenReturn(11L);
        when(supportDAO.findMyPage(7L, 10, 10)).thenReturn(List.of(inquiry(11L, 7L)));

        SupportInquiryPage result = service.getMine(7L, 1, 10);

        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.inquiries()).hasSize(1);
    }

    @Test
    void memberCannotReadAnotherMembersInquiry() {
        when(supportDAO.findInquiry(11L)).thenReturn(Optional.of(inquiry(11L, 99L)));

        assertThatThrownBy(() -> service.getMineDetail(7L, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void memberCanReadOwnInquiryDetail() {
        SupportInquiryDTO mine = inquiry(11L, 7L);
        when(supportDAO.findInquiry(11L)).thenReturn(Optional.of(mine));
        SupportInquiryDTO result = service.getMineDetail(7L, 11L);

        assertThat(result).isSameAs(mine);
    }

    private SupportInquiryDTO inquiry(Long inquiryId, Long userId) {
        return SupportInquiryDTO.builder()
                .supportInquiryId(inquiryId)
                .userId(userId)
                .category("AI_PLAN")
                .title("일정 문의")
                .content("문의 내용")
                .status("OPEN")
                .build();
    }
}
