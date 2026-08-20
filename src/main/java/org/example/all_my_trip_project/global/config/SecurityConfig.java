package org.example.all_my_trip_project.global.config;

import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    // 비로그인으로 열람할 수 있는 화면. 여기에 없는 화면은 아래 체인에서 명시적으로 분류한다.
    private static final String[] PUBLIC_PAGES = {
            "/", "/home",
            "/auth/**",
            "/guide", "/guide/**",
            "/booking", "/booking/**",
            // AI 화면 자체는 열어둔다. 실제 생성 요청은 /api/v1/ai-guides/generate에서 인증을 요구한다.
            "/ai-guide", "/ai-trip-plan",
            "/error"
    };

    private static final String[] AUTHENTICATED_PAGES = {
            // /pay/**는 QR 결제 승인 화면이다. 승인은 QR을 띄운 본인만 할 수 있어야 한다. (#281)
            "/trips/**", "/mypage", "/pay/**"
    };

    // SockJS 핸드셰이크(및 폴백 전송의 XHR POST)가 지나가는 경로. 인증은 여기서 요구하고,
    // CSRF는 스프링 시큐리티의 기본 필터가 아니라 STOMP CONNECT 프레임 안에서 직접 검증한다
    // (SupportChatChannelInterceptor) — 설계 문서 §2 "인증(확정)".
    private static final String WEBSOCKET_PATH = "/ws/support-chat/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ui 프로필에는 로그인 API(AuthController가 @Profile("!ui"))가 아예 등록되지 않아
    // 인증 수단이 없다. 화면 연결 작업이 막히지 않도록 ui에서는 종전대로 전부 열어둔다.
    @Bean
    @Profile("ui")
    public SecurityFilterChain permissivePageFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    // /api/** 체인(ApiSecurityConfig, @Order(1))이 먼저 매칭되고 이 체인이 나머지 페이지 요청을 받는다.
    @Bean
    @Profile("!ui")
    public SecurityFilterChain pageSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<UserRepository> users
    ) throws Exception {
        // 화면 체인에도 같이 건다. API만 막으면 /admin 화면은 정지된 관리자에게 계속 열린다. (#238)
        ApiSecurityConfig.addAccountStatusFilter(http, users);

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(PUBLIC_PAGES).permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers(AUTHENTICATED_PAGES).authenticated()
                        .requestMatchers(WEBSOCKET_PATH).authenticated()
                        .anyRequest().permitAll()
                )
                // 페이지 요청은 JSON이 아니라 화면을 기대하므로 401 본문 대신 로그인 화면으로 보낸다.
                // API 체인은 401 JSON을 그대로 유지한다. (#95)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(loginRedirectEntryPoint()))
                // STOMP CONNECT 프레임 안에서 직접 CSRF를 검증하므로, 이 경로는 스프링
                // 시큐리티의 CsrfFilter 대상에서 뺀다(안 빼면 SockJS 폴백 전송의 POST가 막힌다).
                .csrf(csrf -> csrf.ignoringRequestMatchers(WEBSOCKET_PATH))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    // 로그인 후 돌아올 주소를 쿼리 파라미터로 넘긴다. 로그인은 자체 API(POST /api/v1/auth/login)로
    // 처리하므로 시큐리티의 SavedRequest가 소비되지 않아, 프론트가 읽을 수 있는 형태로 전달한다.
    private AuthenticationEntryPoint loginRedirectEntryPoint() {
        return (request, response, exception) -> {
            String target = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                target = target + "?" + query;
            }
            response.sendRedirect("/auth/login?redirect="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
        };
    }
}
