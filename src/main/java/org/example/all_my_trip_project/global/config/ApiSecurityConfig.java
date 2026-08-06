package org.example.all_my_trip_project.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class ApiSecurityConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieName("CSRF-TOKEN");
        csrfRepository.setHeaderName("X-CSRF-TOKEN");
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .httpOnly(false)
                .sameSite("Lax")
                .path("/"));
        return csrfRepository;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, CsrfTokenRepository csrfRepository) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai-guides/generate").authenticated()
                        // 장소는 여러 사용자의 일정이 참조하는 공용 데이터이므로 생성은 로그인 사용자만 허용한다.
                        // 조회(GET)는 비로그인 탐색을 허용해야 하므로 permitAll을 유지한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/places").authenticated()
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(requestHandler))
                // 로그인하지 않은 요청은 401, 로그인했지만 권한이 없거나 CSRF 토큰이 없는 요청은 403으로 구분한다.
                // 기본 설정에서는 둘 다 403이라 프론트에서 로그인 유도 분기를 태울 수 없었다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> writeError(
                response,
                ErrorCode.UNAUTHORIZED.getStatus(),
                ErrorCode.UNAUTHORIZED.name(),
                ErrorCode.UNAUTHORIZED.getMessage()
        );
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> writeError(
                response,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "요청을 수행할 권한이 없습니다."
        );
    }

    // 응답 본문 형식을 ApiExceptionHandler의 ErrorResponse와 동일하게 맞춰,
    // 프론트가 한 가지 형태만 처리하도록 한다.
    // code와 message는 이 클래스가 지정하는 고정 문자열이라 별도 이스케이프가 필요 없다.
    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"success\":false,"
                        + "\"code\":\"" + code + "\","
                        + "\"message\":\"" + message + "\","
                        + "\"data\":null,"
                        + "\"errors\":[]}"
        );
    }
}
