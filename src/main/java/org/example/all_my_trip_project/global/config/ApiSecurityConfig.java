package org.example.all_my_trip_project.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.example.all_my_trip_project.global.exception.ErrorCode;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class ApiSecurityConfig {

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
            CsrfTokenRepository csrfRepository
    ) throws Exception {

        CsrfTokenRequestAttributeHandler requestHandler =
                new CsrfTokenRequestAttributeHandler();

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
