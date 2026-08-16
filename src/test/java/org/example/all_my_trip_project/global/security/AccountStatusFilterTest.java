package org.example.all_my_trip_project.global.security;

import jakarta.servlet.FilterChain;
import org.example.all_my_trip_project.domain.user.repository.UserAccountView;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountStatusFilterTest {

    private static final long USER_ID = 7L;

    private UserRepository userRepository;
    private AccountStatusFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        filter = new AccountStatusFilter(userRepository);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/audit-logs");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession login(String role) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(USER_ID, "admin@example.com", role),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        request.setSession(session);
        return session;
    }

    private void account(String status, String role) {
        UserAccountView view = mock(UserAccountView.class);
        when(view.getStatus()).thenReturn(status);
        when(view.getRole()).thenReturn(role);
        when(userRepository.findAccountByUserId(USER_ID)).thenReturn(Optional.of(view));
    }

    private AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }

    @Test
    @DisplayName("정지된 계정은 살아 있던 세션이 끊긴다")
    void terminatesSessionWhenSuspended() throws Exception {
        MockHttpSession session = login("ADMIN");
        account("SUSPENDED", "ADMIN");

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        /* 응답은 뒤따르는 인가 필터가 만든다. 여기서 막지 않고 그대로 넘긴다. */
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("탈퇴한 계정도 세션이 끊긴다")
    void terminatesSessionWhenWithdrawn() throws Exception {
        MockHttpSession session = login("USER");
        account("WITHDRAWN", "USER");

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("계정이 사라졌으면 세션도 끊는다")
    void terminatesSessionWhenAccountGone() throws Exception {
        MockHttpSession session = login("ADMIN");
        when(userRepository.findAccountByUserId(USER_ID)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("관리자 권한이 해제되면 내보내지 않고 권한만 갈아 끼운다")
    void refreshesRoleWhenDemoted() throws Exception {
        MockHttpSession session = login("ADMIN");
        account("ACTIVE", "USER");

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(principal().role()).isEqualTo("USER");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    /*
     * 요청 범위의 컨텍스트만 바꾸면 다음 요청에서 세션에 남은 옛 권한이 그대로 다시 올라온다.
     * 한 번 강등된 관리자가 새로고침만으로 되살아나는 셈이라 여기를 반드시 확인한다.
     */
    @Test
    @DisplayName("바뀐 권한을 세션에도 덮어써 다음 요청까지 유지한다")
    void persistsRefreshedRoleToSession() throws Exception {
        MockHttpSession session = login("ADMIN");
        account("ACTIVE", "USER");

        filter.doFilter(request, response, chain);

        var stored = (org.springframework.security.core.context.SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        AuthenticatedUser storedUser = (AuthenticatedUser) stored.getAuthentication().getPrincipal();
        assertThat(storedUser.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("승격된 권한도 즉시 반영한다")
    void refreshesRoleWhenPromoted() throws Exception {
        login("USER");
        account("ACTIVE", "ADMIN");

        filter.doFilter(request, response, chain);

        assertThat(principal().role()).isEqualTo("ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("달라진 것이 없으면 세션을 그대로 둔다")
    void leavesUnchangedAccountAlone() throws Exception {
        MockHttpSession session = login("ADMIN");
        account("ACTIVE", "ADMIN");

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(principal().role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 조회하지 않는다")
    void skipsAnonymousRequests() throws Exception {
        filter.doFilter(request, response, chain);

        verify(userRepository, never()).findAccountByUserId(any());
        verify(chain).doFilter(request, response);
    }

    /*
     * DB가 잠깐 흔들렸다고 로그인한 사람을 전부 튕기면 장애가 훨씬 커진다. 확인을 못 한 것이지
     * 계정에 문제가 있다는 뜻이 아니다.
     */
    @Test
    @DisplayName("계정 조회에 실패하면 기존 세션을 유지한다")
    void keepsSessionWhenLookupFails() throws Exception {
        MockHttpSession session = login("ADMIN");
        when(userRepository.findAccountByUserId(USER_ID))
                .thenThrow(new RuntimeException("데이터베이스 연결 실패"));

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(principal().role()).isEqualTo("ADMIN");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("정적 리소스 요청은 계정을 조회하지 않는다")
    void skipsStaticResources() throws Exception {
        login("ADMIN");
        request.setRequestURI("/css/pages/admin/admin.css");

        filter.doFilter(request, response, chain);

        verify(userRepository, never()).findAccountByUserId(any());
        verify(chain).doFilter(request, response);
    }
}
