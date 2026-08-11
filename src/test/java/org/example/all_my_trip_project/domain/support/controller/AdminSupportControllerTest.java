package org.example.all_my_trip_project.domain.support.controller;

import org.example.all_my_trip_project.domain.support.dto.SupportReplyRequest;
import org.example.all_my_trip_project.domain.support.service.AdminSupportService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSupportControllerTest {
    @Mock private AdminSupportService service;
    @InjectMocks private AdminSupportController controller;

    @Test
    void administratorCanRegisterReply() {
        AuthenticatedUser admin = new AuthenticatedUser(9L, "admin@example.com", "ADMIN");
        SupportReplyRequest request = new SupportReplyRequest("확인 후 답변드립니다.");

        controller.reply(admin, 11L, request);

        verify(service).reply(9L, 11L, request);
    }

    @Test
    void normalMemberCannotReadAdminInquiryList() {
        AuthenticatedUser member = new AuthenticatedUser(7L, "member@example.com", "USER");

        assertThatThrownBy(() -> controller.list(member, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
