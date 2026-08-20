package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    @Mock
    private PlaceService placeService;

    @InjectMocks
    private PlaceController placeController;
    private MockMvc mockMvc;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(placeController).build();
    }

    @Test
    void listUsesAuthenticatedUserForFavoriteOrdering() {
        when(placeService.getPage(42L, false, 0, 20)).thenReturn(List.of());

        assertThat(placeController.list(principal, 0, 20, null, null, null, null, false).data())
                .isEmpty();

        verify(placeService).getPage(42L, false, 0, 20);
    }

    @Test
    void anonymousListKeepsPublicPlaceLookup() {
        when(placeService.getPage(null, false, 0, 20)).thenReturn(List.of());

        assertThat(placeController.list(null, 0, 20, null, null, null, null, false).data())
                .isEmpty();

        verify(placeService).getPage(null, false, 0, 20);
    }

    @Test
    void searchUsesAuthenticatedUserForFavoriteOrdering() {
        when(placeService.search(42L, false, "서울", "CAFE", null, null, 0, 20))
                .thenReturn(List.of());

        assertThat(placeController.list(principal, 0, 20, "서울", "CAFE", null, null, false).data())
                .isEmpty();

        verify(placeService).search(42L, false, "서울", "CAFE", null, null, 0, 20);
    }

    @Test
    void listUsesVersionedPathDefaultsAndCommonResponse() throws Exception {
        PlaceDTO place = PlaceDTO.builder().placeId(1L).name("해운대").build();
        when(placeService.getPage(null, false, 0, 20)).thenReturn(List.of(place));

        mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].placeId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("해운대"));

        verify(placeService).getPage(null, false, 0, 20);
    }
}
