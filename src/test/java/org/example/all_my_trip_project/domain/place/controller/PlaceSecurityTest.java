package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PlaceController는 @Profile("!ui")이고 기본 프로필이 ui이므로, 빈이 등록되도록 test 프로필을 활성화한다.
@WebMvcTest(PlaceController.class)
@Import(ApiSecurityConfig.class)
@ActiveProfiles("test")
class PlaceSecurityTest {

    private static final String PLACE_JSON = """
            {"externalProvider":"KAKAO","externalPlaceId":"1","category":"ATTRACTION",
             "name":"성산일출봉","countryCode":"KR","region":"제주","city":"서귀포시","active":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceService placeService;

    @Test
    void rejectsUnauthenticatedPlaceCreationEvenWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/places")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_JSON))
                .andExpect(status().isUnauthorized());
    }

    // CsrfFilter는 ExceptionTranslationFilter보다 앞에서 동작하며 자체 AccessDeniedHandler로 응답한다.
    // 따라서 CSRF 실패는 로그인 여부와 무관하게 항상 403이고, 401은 인증 실패에만 사용된다.
    @Test
    void rejectsUnauthenticatedPlaceCreationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAuthenticatedPlaceCreationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/places")
                        .with(authentication(authenticatedUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsNormalUserPlaceCreationEvenWithValidCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/places")
                        .with(authentication(authenticatedUser()))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAdminPlaceCreationWithValidCsrfToken() throws Exception {
        when(placeService.create(any(PlaceDTO.class))).thenReturn(1L);
        when(placeService.get(1L)).thenReturn(new PlaceDTO());

        mockMvc.perform(post("/api/v1/places")
                        .with(authentication(adminUser()))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void allowsAnonymousPlaceLookup() throws Exception {
        when(placeService.getPage(null, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "place-test@example.com", "USER"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private UsernamePasswordAuthenticationToken adminUser() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "place-admin@example.com", "ADMIN"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
