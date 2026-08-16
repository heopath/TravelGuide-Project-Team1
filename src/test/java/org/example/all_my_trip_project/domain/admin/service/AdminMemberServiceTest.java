package org.example.all_my_trip_project.domain.admin.service;

import org.example.all_my_trip_project.domain.admin.dao.AdminMemberDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이 테스트의 무게중심은 두 보호장치다. 목록 조회는 다른 관리자 화면과 같은 모양이라
 * 탈퇴 회원을 언제 넣고 빼는지만 본다.
 */
class AdminMemberServiceTest {

    private static final long CURRENT_ADMIN_ID = 7L;

    private AdminMemberDAO adminMemberDAO;
    private AdminAuditService adminAuditService;
    private AdminMemberService service;

    @BeforeEach
    void setUp() {
        adminMemberDAO = mock(AdminMemberDAO.class);
        adminAuditService = mock(AdminAuditService.class);
        service = new AdminMemberService(adminMemberDAO, adminAuditService);
        login(CURRENT_ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, "admin@example.com", "ADMIN"),
                        null, List.of()));
    }

    private AdminMemberDTO member(long userId, String role, String status) {
        return AdminMemberDTO.builder()
                .userId(userId)
                .email("member" + userId + "@example.com")
                .nickname("회원" + userId)
                .role(role)
                .status(status)
                .createdAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"))
                .build();
    }

    private void existing(AdminMemberDTO member) {
        when(adminMemberDAO.findById(member.getUserId())).thenReturn(Optional.of(member));
    }

    /* ── 자기 자신 보호 ── */

    @Test
    @DisplayName("자기 자신은 정지할 수 없다")
    void rejectsSuspendingSelf() {
        existing(member(CURRENT_ADMIN_ID, "ADMIN", "ACTIVE"));

        assertThatThrownBy(() -> service.changeStatus(CURRENT_ADMIN_ID, "SUSPENDED", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_SELF_CHANGE_DENIED);

        verify(adminMemberDAO, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("자기 자신의 관리자 권한은 내릴 수 없다")
    void rejectsDemotingSelf() {
        existing(member(CURRENT_ADMIN_ID, "ADMIN", "ACTIVE"));

        assertThatThrownBy(() -> service.changeRole(CURRENT_ADMIN_ID, "USER", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_SELF_CHANGE_DENIED);

        verify(adminMemberDAO, never()).updateRole(any(), any());
    }

    /* ── 마지막 관리자 보호 ── */

    @Test
    @DisplayName("활동 중인 관리자가 하나뿐이면 그 관리자를 강등할 수 없다")
    void rejectsDemotingLastAdmin() {
        existing(member(9L, "ADMIN", "ACTIVE"));
        when(adminMemberDAO.lockAndCountActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(9L, "USER", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);

        verify(adminMemberDAO, never()).updateRole(any(), any());
    }

    @Test
    @DisplayName("활동 중인 관리자가 하나뿐이면 그 관리자를 정지할 수도 없다")
    void rejectsSuspendingLastAdmin() {
        existing(member(9L, "ADMIN", "ACTIVE"));
        when(adminMemberDAO.lockAndCountActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(9L, "SUSPENDED", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);

        verify(adminMemberDAO, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("관리자가 둘 이상 남으면 강등할 수 있다")
    void allowsDemotingWhenAnotherAdminRemains() {
        existing(member(9L, "ADMIN", "ACTIVE"));
        when(adminMemberDAO.lockAndCountActiveAdmins()).thenReturn(2L);
        when(adminMemberDAO.updateRole(9L, "USER")).thenReturn(1);
        when(adminMemberDAO.findById(9L))
                .thenReturn(Optional.of(member(9L, "ADMIN", "ACTIVE")))
                .thenReturn(Optional.of(member(9L, "USER", "ACTIVE")));

        AdminMemberDTO result = service.changeRole(9L, "USER", "인수인계 완료");

        assertThat(result.getRole()).isEqualTo("USER");
        verify(adminMemberDAO).updateRole(9L, "USER");
        verify(adminAuditService).record(eq("MEMBER_ROLE_CHANGE"), eq("USER"), eq(9L), any(), any());
    }

    @Test
    @DisplayName("일반 회원을 정지할 때는 관리자 수를 세지 않는다")
    void doesNotLockAdminsForOrdinaryMember() {
        existing(member(9L, "USER", "ACTIVE"));
        when(adminMemberDAO.updateStatus(9L, "SUSPENDED")).thenReturn(1);
        when(adminMemberDAO.findById(9L))
                .thenReturn(Optional.of(member(9L, "USER", "ACTIVE")))
                .thenReturn(Optional.of(member(9L, "USER", "SUSPENDED")));

        AdminMemberDTO result = service.changeStatus(9L, "SUSPENDED", "약관 위반");

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        verify(adminMemberDAO, never()).lockAndCountActiveAdmins();
    }

    /* ── 그 밖의 규칙 ── */

    @Test
    @DisplayName("탈퇴한 회원은 상태도 권한도 바꿀 수 없다")
    void rejectsChangingWithdrawnMember() {
        existing(member(9L, "USER", "WITHDRAWN"));

        assertThatThrownBy(() -> service.changeStatus(9L, "ACTIVE", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_WITHDRAWN_IMMUTABLE);
        assertThatThrownBy(() -> service.changeRole(9L, "ADMIN", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_WITHDRAWN_IMMUTABLE);
    }

    @Test
    @DisplayName("정지된 회원은 관리자로 올릴 수 없다")
    void rejectsPromotingSuspendedMember() {
        existing(member(9L, "USER", "SUSPENDED"));

        assertThatThrownBy(() -> service.changeRole(9L, "ADMIN", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_ACTIVE);

        verify(adminMemberDAO, never()).updateRole(any(), any());
    }

    @Test
    @DisplayName("이미 같은 상태면 바꾸지 않고 그대로 돌려준다")
    void skipsWhenAlreadyInTargetStatus() {
        existing(member(9L, "USER", "ACTIVE"));

        AdminMemberDTO result = service.changeStatus(9L, "ACTIVE", null);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(adminMemberDAO, never()).updateStatus(any(), any());
        verify(adminAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("없는 회원을 바꾸려 하면 찾을 수 없다고 알린다")
    void rejectsUnknownMember() {
        when(adminMemberDAO.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(404L, "SUSPENDED", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("WITHDRAWN 말고 다른 상태로는 탈퇴 회원을 목록에 넣지 않는다")
    void excludesWithdrawnUnlessFiltered() {
        when(adminMemberDAO.count(any(), any(), any(), anyBoolean())).thenReturn(0L);
        when(adminMemberDAO.findPage(any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list(0, 20, null, null, null);
        verify(adminMemberDAO).count(null, null, null, false);

        service.list(0, 20, null, "WITHDRAWN", null);
        verify(adminMemberDAO).count(null, "WITHDRAWN", null, true);
    }

    @Test
    @DisplayName("검색 조건은 대소문자를 가리지 않는다")
    void normalizesFilters() {
        when(adminMemberDAO.count(any(), any(), any(), anyBoolean())).thenReturn(0L);
        when(adminMemberDAO.findPage(any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list(0, 20, "  민재 ", "active", "admin");

        verify(adminMemberDAO).count("민재", "ACTIVE", "ADMIN", false);
    }

    @Test
    @DisplayName("알 수 없는 상태 필터는 거부한다")
    void rejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> service.list(0, 20, null, "DELETED", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ADMIN_REQUEST);
    }
}
