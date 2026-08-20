package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageFillResult;
import org.example.all_my_trip_project.domain.place.service.AdminPlaceService;
import org.example.all_my_trip_project.domain.place.service.PlaceImageBackfillService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockitoBean
    private PlaceImageBackfillService placeImageBackfillService;

    private static final String IMAGE_FILL_REQUEST = """
            {"placeIds":[1,2,3]}
            """;

    /* 이미지 채우기는 외부 API를 장소 수만큼 부른다. 아무나 누를 수 있으면 안 된다. */
    @Test
    void rejectsNormalUserOnImageFill() throws Exception {
        mockMvc.perform(post("/api/v1/admin/places/images")
                        .with(authentication(user("USER")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IMAGE_FILL_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminOnImageFill() throws Exception {
        when(placeImageBackfillService.fill(List.of(1L, 2L, 3L)))
                .thenReturn(new PlaceImageFillResult(3, 2, 0, 1));

        mockMvc.perform(post("/api/v1/admin/places/images")
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IMAGE_FILL_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filled").value(2))
                .andExpect(jsonPath("$.data.notFound").value(1));
    }

    /* 빈 선택으로 부르면 외부 API를 헛돌린다. 컨트롤러에서 막는다. */
    @Test
    void rejectsEmptySelectionOnImageFill() throws Exception {
        mockMvc.perform(post("/api/v1/admin/places/images")
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

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
