package org.example.all_my_trip_project.domain.admin.controller;

import org.example.all_my_trip_project.domain.admin.dto.ServiceVersionDTO;
import org.example.all_my_trip_project.domain.admin.service.ServiceVersionService;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceVersionController.class)
@Import(ApiSecurityConfig.class)
@ActiveProfiles("test")
class ServiceVersionSecurityTest {

    private static final String ENDPOINT = "/api/v1/admin/service-settings/footer-version";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceVersionService serviceVersionService;

    @Test
    void rejectsNormalUser() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .with(authentication(user("USER")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v0.9.1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotLoadFooterVersionForJsonApi() throws Exception {
        when(serviceVersionService.get()).thenReturn(new ServiceVersionDTO("v0.9.0"));

        mockMvc.perform(get(ENDPOINT)
                        .with(authentication(user("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v0.9.0"));

        verify(serviceVersionService, never()).displayVersion();
    }

    @Test
    void rejectsAdminWithoutCsrfToken() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v0.9.1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminAndPassesCurrentUserId() throws Exception {
        when(serviceVersionService.update(eq("v0.9.1"), eq(1L)))
                .thenReturn(new ServiceVersionDTO("v0.9.1"));

        mockMvc.perform(put(ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v0.9.1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v0.9.1"));
    }

    @Test
    void rejectsInvalidFormatBeforeServiceCall() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0.9\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken user(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "version-test@example.com", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
