package org.example.all_my_trip_project.domain.admin.service;

import org.example.all_my_trip_project.domain.admin.dao.AdminAuditDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogPage;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminAuditServiceTest {

    private AdminAuditDAO adminAuditDAO;
    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        adminAuditDAO = mock(AdminAuditDAO.class);
        service = new AdminAuditService(adminAuditDAO);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, "admin@example.com", "ADMIN"), null, List.of()));
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private AdminAuditLogDTO captured() {
        ArgumentCaptor<AdminAuditLogDTO> captor = ArgumentCaptor.forClass(AdminAuditLogDTO.class);
        verify(adminAuditDAO).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("로그인한 관리자와 대상, 전후 값을 함께 남긴다")
    void recordsAdminAndPayload() {
        loginAs(7L);

        service.record("TICKET_PRODUCT_STATUS_CHANGE", "TICKET_PRODUCT", 42L,
                Map.of("status", "ON_SALE"), Map.of("status", "SOLD_OUT"));

        AdminAuditLogDTO log = captured();
        assertThat(log.getAdminUserId()).isEqualTo(7L);
        assertThat(log.getActionType()).isEqualTo("TICKET_PRODUCT_STATUS_CHANGE");
        assertThat(log.getTargetType()).isEqualTo("TICKET_PRODUCT");
        assertThat(log.getTargetId()).isEqualTo("42");
        assertThat(log.getBeforeData()).contains("ON_SALE");
        assertThat(log.getAfterData()).contains("SOLD_OUT");
    }

    @Test
    @DisplayName("기록에 실패해도 예외를 밖으로 던지지 않는다")
    void neverThrowsWhenWriteFails() {
        loginAs(7L);
        given(adminAuditDAO.insert(any())).willThrow(new RuntimeException("DB down"));

        assertThatCode(() -> service.record("PLACE_UPDATE", "PLACE", 1L, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증 정보가 없어도 무엇을 했는지는 남긴다")
    void recordsWithoutAuthentication() {
        service.record("PLACE_UPDATE", "PLACE", 1L, null, Map.of("name", "제주"));

        AdminAuditLogDTO log = captured();
        assertThat(log.getAdminUserId()).isNull();
        assertThat(log.getActionType()).isEqualTo("PLACE_UPDATE");
    }

    @Test
    @DisplayName("비어 있는 전후 값은 null로 남겨 빈 JSON을 만들지 않는다")
    void leavesEmptyPayloadNull() {
        loginAs(7L);

        service.record("PLACE_UPDATE", "PLACE", 1L, Map.of(), null);

        AdminAuditLogDTO log = captured();
        assertThat(log.getBeforeData()).isNull();
        assertThat(log.getAfterData()).isNull();
    }

    @Test
    @DisplayName("프록시 뒤에서는 X-Forwarded-For의 첫 주소를 남긴다")
    void prefersForwardedClientIp() {
        loginAs(7L);
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.setRemoteAddr("10.0.0.1");

        service.record("PLACE_UPDATE", "PLACE", 1L, null, null);

        assertThat(captured().getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("IP 형식이 아니면 비운다 — INET 캐스팅 실패로 이력 전체를 잃지 않는다")
    void dropsMalformedIp() {
        loginAs(7L);
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Forwarded-For", "unknown");

        service.record("PLACE_UPDATE", "PLACE", 1L, null, null);

        assertThat(captured().getIpAddress()).isNull();
    }

    @Test
    @DisplayName("User-Agent가 컬럼 길이를 넘으면 잘라서라도 남긴다")
    void truncatesLongUserAgent() {
        loginAs(7L);
        MockHttpServletRequest request = bindRequest();
        request.addHeader("User-Agent", "x".repeat(700));

        service.record("PLACE_UPDATE", "PLACE", 1L, null, null);

        assertThat(captured().getUserAgent()).hasSize(500);
    }

    @Test
    @DisplayName("요청 밖에서 불려도 IP·UA 없이 기록한다")
    void recordsOutsideRequestScope() {
        loginAs(7L);

        service.record("PLACE_UPDATE", "PLACE", 1L, null, null);

        AdminAuditLogDTO log = captured();
        assertThat(log.getIpAddress()).isNull();
        assertThat(log.getUserAgent()).isNull();
        assertThat(log.getAdminUserId()).isEqualTo(7L);
    }

    /* ── 조회 ── */

    @Test
    @DisplayName("빈 필터는 null로 넘겨 조건에서 빠지게 한다")
    void passesBlankFiltersAsNull() {
        service.list(0, 30, "  ", "", "  ", null);

        verify(adminAuditDAO).countView(null, null, null, null);
        verify(adminAuditDAO).findView(null, null, null, null, 0, 30);
    }

    @Test
    @DisplayName("페이지 크기가 상한을 넘으면 거부한다")
    void rejectsTooLargePageSize() {
        assertThatThrownBy(() -> service.list(0, 500, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("음수 페이지는 거부한다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> service.list(-1, 30, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("전체 건수가 0이면 페이지 수도 0이다")
    void reportsZeroPagesWhenEmpty() {
        given(adminAuditDAO.countView(any(), any(), any(), any())).willReturn(0L);

        AdminAuditLogPage result = service.list(0, 30, null, null, null, null);

        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("실제로 쌓인 동작 종류를 함께 내려보낸다")
    void exposesRecordedActionTypes() {
        given(adminAuditDAO.countView(any(), any(), any(), any())).willReturn(2L);
        given(adminAuditDAO.findActionTypes()).willReturn(List.of("PLACE_UPDATE", "REPORT_PROCESS"));

        AdminAuditLogPage result = service.list(0, 30, null, null, null, null);

        assertThat(result.actionTypes()).containsExactly("PLACE_UPDATE", "REPORT_PROCESS");
    }
}
