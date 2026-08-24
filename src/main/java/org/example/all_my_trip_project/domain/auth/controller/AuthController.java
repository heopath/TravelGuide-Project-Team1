package org.example.all_my_trip_project.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.auth.dto.LoginRequest;
import org.example.all_my_trip_project.domain.auth.dto.SignupRequest;
import org.example.all_my_trip_project.domain.auth.service.AuthService;
import org.example.all_my_trip_project.domain.user.dto.MemberResponse;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.example.all_my_trip_project.global.security.turnstile.TurnstileVerifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TurnstileVerifier turnstileVerifier;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest
    ) {
        turnstileVerifier.verify(request.turnstileToken(), "signup", servletRequest.getRemoteAddr());
        MemberResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "회원가입이 완료되었습니다.",
                        response
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        turnstileVerifier.verify(request.turnstileToken(), "login", servletRequest.getRemoteAddr());
        MemberResponse response = authService.login(request);

        AuthenticatedUser principal = new AuthenticatedUser(
                response.userId(),
                response.email(),
                response.role()
        );

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + response.role()
                        ))
                );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();

        session.setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "로그인되었습니다.",
                        response
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (principal == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        /*
         * 지우는 쿠키에도 발급 때와 같은 보안 속성을 준다. 운영은 https라 Secure로 나가고,
         * 로컬(http)에서는 꺼진다 — 로컬에서 켜 두면 브라우저가 그 쿠키를 무시해 로그아웃이
         * 안 된다. (CodeQL java/insecure-cookie)
         */
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(
                ApiResponse.<Void>success(
                        "로그아웃되었습니다.",
                        null
                )
        );
    }
}
