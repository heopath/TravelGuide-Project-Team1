package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.AdminPlaceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlaceController.class)
@Import(ApiSecurityConfig.class)
@ActiveProfiles("test")
class AdminPlaceSecurityTest {

    private static final String REQUEST = """
            {"category":"ATTRACTION","name":"성산일출봉","countryCode":"KR","active":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPlaceService adminPlaceService;

    @Test
    void rejectsNormalUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/places")
                        .with(authentication(user("USER")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminWithCsrfToken() throws Exception {
        when(adminPlaceService.create(any())).thenReturn(PlaceDTO.builder().placeId(1L).build());

        mockMvc.perform(post("/api/v1/admin/places")
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken user(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "admin-place@example.com", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
