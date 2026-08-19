package org.example.all_my_trip_project.domain.admin.service;

import org.example.all_my_trip_project.domain.admin.dao.ServiceSettingDAO;
import org.example.all_my_trip_project.domain.admin.dto.ServiceVersionDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServiceVersionServiceTest {

    private ServiceSettingDAO serviceSettingDAO;
    private AdminAuditService adminAuditService;
    private ServiceVersionService service;

    @BeforeEach
    void setUp() {
        serviceSettingDAO = mock(ServiceSettingDAO.class);
        adminAuditService = mock(AdminAuditService.class);
        service = new ServiceVersionService(serviceSettingDAO, adminAuditService);
    }

    @Test
    @DisplayName("설정 행이 없으면 현재 기본 버전을 표시한다")
    void usesDefaultWhenSettingIsMissing() {
        given(serviceSettingDAO.findValue("footer.version")).willReturn(Optional.empty());

        assertThat(service.get().version()).isEqualTo("v0.9.0");
    }

    @Test
    @DisplayName("v 접두어를 제거해 저장하고 표시할 때 한 번만 붙인다")
    void normalizesVersionBeforeSaving() {
        given(serviceSettingDAO.findValue("footer.version")).willReturn(Optional.of("0.9.0"));

        ServiceVersionDTO result = service.update(" v0.9.1 ", 7L);

        verify(serviceSettingDAO).upsert("footer.version", "0.9.1", 7L);
        assertThat(result.version()).isEqualTo("v0.9.1");
    }

    @Test
    @DisplayName("버전 변경 전후 값을 관리자 감사 로그에 남긴다")
    void recordsAuditLog() {
        given(serviceSettingDAO.findValue("footer.version")).willReturn(Optional.of("0.9.0"));

        service.update("0.10.0", 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditService).record(
                org.mockito.ArgumentMatchers.eq("SERVICE_VERSION_CHANGE"),
                org.mockito.ArgumentMatchers.eq("SERVICE_SETTING"),
                org.mockito.ArgumentMatchers.eq("footer.version"),
                before.capture(), after.capture());
        assertThat(before.getValue()).containsEntry("version", "v0.9.0");
        assertThat(after.getValue()).containsEntry("version", "v0.10.0");
    }

    @Test
    @DisplayName("이미 같은 버전이면 DB와 감사 로그를 중복 기록하지 않는다")
    void skipsUnchangedVersion() {
        given(serviceSettingDAO.findValue("footer.version")).willReturn(Optional.of("0.9.1"));

        service.update("v0.9.1", 7L);

        verify(serviceSettingDAO, never()).upsert("footer.version", "0.9.1", 7L);
        verify(adminAuditService, never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("세 자리 형식이 아닌 버전은 거부한다")
    void rejectsInvalidVersion() {
        assertThatThrownBy(() -> service.update("0.9", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SERVICE_VERSION);
    }
}
