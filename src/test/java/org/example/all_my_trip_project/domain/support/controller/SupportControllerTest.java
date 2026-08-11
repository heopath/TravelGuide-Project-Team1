package org.example.all_my_trip_project.domain.support.controller;

import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryRequest;
import org.example.all_my_trip_project.domain.support.service.SupportService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportControllerTest {
    @Mock private SupportService supportService;
    @InjectMocks private SupportController controller;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(7L, "member@example.com", "USER");

    @Test
    void createUsesAuthenticatedMember() {
        SupportInquiryRequest request = new SupportInquiryRequest("OTHER", "문의", "문의 내용입니다.");
        when(supportService.create(7L, request))
                .thenReturn(SupportInquiryDTO.builder().supportInquiryId(11L).build());

        controller.create(principal, request);

        verify(supportService).create(7L, request);
    }

    @Test
    void anonymousMemberCannotListInquiries() {
        assertThatThrownBy(() -> controller.mine(null, 0, 10))
                .isInstanceOf(BusinessException.class);
    }
}
