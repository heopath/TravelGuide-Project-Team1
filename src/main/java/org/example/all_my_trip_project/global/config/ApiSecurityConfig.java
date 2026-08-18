package org.example.all_my_trip_project.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AccountStatusFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class ApiSecurityConfig {

    /**
     * 로그인 뒤에 계정이 바뀌었는지 확인하는 필터를 인가 앞에 끼운다(#238).
     *
     * <p>인가보다 앞이어야 한다. 뒤에 두면 정지된 관리자가 {@code hasRole("ADMIN")}을 이미
     * 통과한 뒤라 늦는다.
     *
     * <p>빈으로 등록하지 않고 직접 만든다. 필터를 빈으로 두면 부트가 시큐리티 체인 밖의
     * 서블릿 필터로도 등록해 요청마다 두 번 돈다. {@code ui} 프로필은 JPA를 빼서
     * {@link UserRepository}가 없고 로그인 자체가 없으므로 그때는 달지 않는다.
     */
    static void addAccountStatusFilter(HttpSecurity http, ObjectProvider<UserRepository> users) {
        UserRepository userRepository = users.getIfAvailable();
        if (userRepository == null) return;
        http.addFilterBefore(new AccountStatusFilter(userRepository), AuthorizationFilter.class);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfRepository =
                new CookieCsrfTokenRepository();

        csrfRepository.setCookieName("CSRF-TOKEN");
        csrfRepository.setHeaderName("X-CSRF-TOKEN");

        csrfRepository.setCookieCustomizer(
                cookie -> cookie
                        .httpOnly(false)
                        .sameSite("Lax")
                        .path("/")
        );

        return csrfRepository;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfRepository,
            ObjectProvider<UserRepository> users
    ) throws Exception {

        CsrfTokenRequestAttributeHandler requestHandler =
                new CsrfTokenRequestAttributeHandler();

        addAccountStatusFilter(http, users);

        return http
                .securityMatcher("/api/**")

                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/v1/members/me",
                                "/api/v1/members/me/**"
                        )
                        .authenticated()

                        .requestMatchers("/api/v1/support/**")
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/ai-guides/generate"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/ai-guides/conversation"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/ai-trip-plans/save"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/places"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/places/*/reviews"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/place-reviews/*"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/place-reviews/*"
                        )
                        .authenticated()

                        // 결제와 발권. 남의 예약을 건드리지 못하게 서비스가 소유자를 다시 본다.
                        .requestMatchers(
                                "/api/v1/ticket-reservations/*/payment",
                                "/api/v1/ticket-reservations/*/tickets"
                        )
                        .authenticated()

                        .anyRequest()
                        .permitAll()
                )

                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                csrfRepository
                        )
                        .csrfTokenRequestHandler(
                                requestHandler
                        )
                )

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                unauthorizedEntryPoint()
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler()
                        )
                )

                .formLogin(
                        AbstractHttpConfigurer::disable
                )

                .httpBasic(
                        AbstractHttpConfigurer::disable
                )

                .build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) ->
                writeError(
                        response,
                        ErrorCode.UNAUTHORIZED.getStatus(),
                        ErrorCode.UNAUTHORIZED.name(),
                        ErrorCode.UNAUTHORIZED.getMessage()
                );
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                writeError(
                        response,
                        HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED",
                        "요청을 수행할 권한이 없습니다."
                );
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {

        response.setStatus(status.value());

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        response.getWriter().write(
                "{\"success\":false,"
                        + "\"code\":\"" + code + "\","
                        + "\"message\":\"" + message + "\","
                        + "\"data\":null,"
                        + "\"errors\":[]}"
        );
    }
}
