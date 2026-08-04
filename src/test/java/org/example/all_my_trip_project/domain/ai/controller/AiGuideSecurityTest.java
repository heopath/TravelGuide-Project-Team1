package org.example.all_my_trip_project.domain.ai.controller;

import org.example.all_my_trip_project.domain.ai.service.AiGuideRequestGuard;
import org.example.all_my_trip_project.domain.ai.service.AiGuideService;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiGuideController.class)
@Import(ApiSecurityConfig.class)
class AiGuideSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGuideService aiGuideService;

    @MockitoBean
    private AiGuideRequestGuard requestGuard;

    @Test
    void rejectsUnauthenticatedAiGuideGenerationRequestEvenWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"여행 추천\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAuthenticatedAiGuideGenerationRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .with(authentication(authenticatedUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"여행 추천\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAuthenticatedAiGuideGenerationRequestWithValidCsrfToken() throws Exception {
        when(aiGuideService.generate(any(), eq(false), eq(1L))).thenReturn(new AiGuideResponse(
                "추천 결과", List.of(), List.of(), List.of("Gemini AI")
        ));

        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .with(authentication(authenticatedUser()))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"여행 추천\"}"))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "ai-test@example.com", "USER"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
