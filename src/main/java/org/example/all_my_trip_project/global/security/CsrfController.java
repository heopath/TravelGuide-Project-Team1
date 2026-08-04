package org.example.all_my_trip_project.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/csrf")
@RequiredArgsConstructor
public class CsrfController {
    private final CsrfTokenRepository csrfTokenRepository;

    @GetMapping
    public Map<String, String> token(CsrfToken csrfToken, HttpServletRequest request,
                                     HttpServletResponse response, Authentication authentication) {
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken()
        );
    }
}
