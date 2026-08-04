package org.example.all_my_trip_project.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionUserResolverTest {

    private final SessionUserResolver resolver = new SessionUserResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserIdFromAuthenticatedUserPrincipal() {
        AuthenticatedUser principal = new AuthenticatedUser(42L, "member@example.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );

        HttpServletRequest request = new MockHttpServletRequest();

        assertThat(resolver.requiredUserId(request)).isEqualTo(42L);
    }
}
