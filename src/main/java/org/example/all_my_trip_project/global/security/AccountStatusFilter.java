package org.example.all_my_trip_project.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.user.repository.UserAccountView;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 로그인한 뒤에 계정이 바뀌었는지 요청마다 확인한다.
 *
 * <p>로그인 시점에는 {@code AuthService.validateAccountStatus()}가 정지·탈퇴를 막지만, 그 뒤에
 * 계정이 바뀌어도 <b>이미 발급된 세션은 그대로 살아 있었다.</b> 정지된 관리자가 자기 세션으로
 * 다른 회원의 정지를 푸는 것까지 됐다(#238).
 *
 * <p>세션을 사용자 번호로 찾아 끊는 방법도 있지만(Spring Security {@code SessionRegistry}),
 * <b>화면을 거치지 않고 바뀐 경우에는 먹지 않는다.</b> 이 서비스는 운영 DB에 SQL을 직접 실행해
 * 권한을 바꾼 전례가 있다(#163). 그래서 바꾼 쪽이 아니라 <b>쓰는 쪽</b>에서 확인한다. 어느 경로로
 * 바뀌든 다음 요청에서 걸린다.
 *
 * <p>값을 캐시하지 않는다. 정지는 보안 통제라 몇 초라도 늦게 먹히면 그만큼 구멍이 남는다.
 * 조회는 기본키 한 건이고, 로그인하지 않은 요청과 정적 리소스는 아예 건너뛴다.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserRepository userRepository;

    /**
     * 정적 리소스는 건너뛴다. 로그인한 사용자가 화면 하나를 열 때마다 CSS·JS·이미지 요청이
     * 줄줄이 따라오는데, 거기까지 세면 한 화면에 조회가 수십 번이 된다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/fonts/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticatedUser user = currentUser();
        if (user != null) {
            apply(request, user);
        }
        filterChain.doFilter(request, response);
    }

    private void apply(HttpServletRequest request, AuthenticatedUser user) {
        Optional<UserAccountView> account;
        try {
            account = userRepository.findAccountByUserId(user.userId());
        } catch (Exception exception) {
            /*
             * DB를 못 읽었다고 로그인된 사람을 전부 튕기지 않는다. 조회 실패는 계정에 문제가
             * 있다는 뜻이 아니라 이쪽 사정이고, 여기서 끊으면 DB가 잠깐 흔들릴 때 전원이
             * 로그아웃된다. 확인을 못 한 것은 로그로 남긴다.
             */
            log.warn("계정 상태를 확인하지 못해 이번 요청은 기존 세션을 유지합니다. userId={}",
                    user.userId(), exception);
            return;
        }

        /* 계정이 사라졌으면 남은 세션도 의미가 없다. */
        if (account.isEmpty() || !STATUS_ACTIVE.equals(account.get().getStatus())) {
            terminate(request, user, account.map(UserAccountView::getStatus).orElse("삭제됨"));
            return;
        }

        String currentRole = account.get().getRole();
        if (!currentRole.equals(user.role())) {
            refreshRole(request, user, currentRole);
        }
    }

    /**
     * 세션을 끊고 인증을 지운다. 응답은 직접 만들지 않는다.
     *
     * <p>인증만 지우면 뒤따르는 인가 필터가 로그인하지 않은 요청으로 보고, API 체인은 401 JSON을,
     * 화면 체인은 로그인 화면으로 보내기를 이미 하고 있다. 여기서 응답을 따로 쓰면 그 두 갈래를
     * 이 필터가 다시 구현하게 된다.
     */
    private void terminate(HttpServletRequest request, AuthenticatedUser user, String status) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        log.info("계정 상태가 바뀌어 세션을 끊었습니다. userId={}, status={}", user.userId(), status);
    }

    /**
     * 권한만 바뀐 경우다. 정지와 달리 계속 쓸 수 있는 상태이므로 내보내지 않고 권한만 갈아 끼운다.
     *
     * <p>세션에 저장된 {@code SecurityContext}까지 덮어써야 한다. 요청 범위의 컨텍스트만 바꾸면
     * 다음 요청에서 세션에 남은 옛 권한이 그대로 다시 올라온다.
     */
    private void refreshRole(HttpServletRequest request, AuthenticatedUser user, String role) {
        AuthenticatedUser updated = new AuthenticatedUser(user.userId(), user.email(), role);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                updated, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
        log.info("권한이 바뀌어 세션에 반영했습니다. userId={}, {} → {}",
                user.userId(), user.role(), role);
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? user : null;
    }
}
