package org.example.all_my_trip_project.global.security;

import org.example.all_my_trip_project.domain.auth.service.AuthService;
import org.example.all_my_trip_project.domain.favorite.controller.FavoriteController;
import org.example.all_my_trip_project.domain.favorite.service.FavoriteService;
import org.example.all_my_trip_project.domain.trip.controller.ItineraryItemController;
import org.example.all_my_trip_project.domain.trip.controller.TripController;
import org.example.all_my_trip_project.domain.trip.dto.TripCreateResult;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.ItineraryItemService;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.domain.user.controller.MemberController;
import org.example.all_my_trip_project.domain.user.dto.MemberResponse;
import org.example.all_my_trip_project.domain.user.service.MemberService;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.example.all_my_trip_project.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CsrfController.class, TripController.class, FavoriteController.class,
        ItineraryItemController.class, MemberController.class
})
@Import({ApiSecurityConfig.class, SecurityConfig.class})
@ActiveProfiles("test")
class CsrfSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TripService tripService;
    @MockitoBean FavoriteService favoriteService;
    @MockitoBean ItineraryItemService itineraryItemService;
    @MockitoBean MemberService memberService;
    @MockitoBean AuthService authService;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");
    private final UsernamePasswordAuthenticationToken authenticated =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

    @Test
    void csrfEndpointIssuesTokenAndReadableCookie() throws Exception {
        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("CSRF-TOKEN"))
                .andExpect(cookie().httpOnly("CSRF-TOKEN", false))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void tripCreationRequiresCsrfHeader() throws Exception {
        TripDTO saved = TripDTO.builder().tripId(10L).userId(42L).build();
        when(tripService.createWithDays(eq(42L), any())).thenReturn(new TripCreateResult(saved, List.of()));

        mockMvc.perform(post("/api/v1/trips").with(authentication(authenticated))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/trips").with(authentication(authenticated)).with(csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void favoriteAddAndRemoveRequireCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/v1/favorites?placeId=100").with(authentication(authenticated)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/favorites?placeId=100").with(authentication(authenticated)).with(csrf().asHeader()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/favorites?placeId=100").with(authentication(authenticated)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/favorites?placeId=100").with(authentication(authenticated)).with(csrf().asHeader()))
                .andExpect(status().isOk());
    }

    @Test
    void itineraryCreationRequiresCsrfHeader() throws Exception {
        when(itineraryItemService.create(eq(42L), any())).thenReturn(99L);
        mockMvc.perform(post("/api/v1/trip-days/20/items").with(authentication(authenticated))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/trip-days/20/items").with(authentication(authenticated)).with(csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void profileUpdateRequiresCsrfHeader() throws Exception {
        when(memberService.updateProfile(eq(42L), any())).thenReturn(
                new MemberResponse(42L, "member@example.com", "여행자", "USER", "ACTIVE"));
        String body = "{\"nickname\":\"새여행자\"}";

        mockMvc.perform(patch("/api/v1/members/me").with(authentication(authenticated))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/members/me").with(authentication(authenticated)).with(csrf().asHeader())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }
}
