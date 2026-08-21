package org.example.all_my_trip_project.domain.place.controller;

import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.domain.place.service.PlaceViewHistoryService;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    @Mock
    private PlaceService placeService;

    @Mock
    private PlaceViewHistoryService placeViewHistoryService;

    @InjectMocks
    private PlaceController placeController;
    private MockMvc mockMvc;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(42L, "member@example.com", "USER");

    @BeforeEach
    void setUp() {
        /*
         * @AuthenticationPrincipal 리졸버를 직접 걸어 준다. 없으면 Spring MVC가
         * AuthenticatedUser를 일반 모델 속성으로 보고 빈 객체를 만들어 넣어서,
         * 비로그인인데도 principal이 null이 아닌 상태로 컨트롤러에 들어온다.
         */
        mockMvc = MockMvcBuilders.standaloneSetup(placeController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
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

    /*
     * /recent가 /{placeId}보다 먼저 잡혀야 한다. 둘 다 GET /api/v1/places/... 라서
     * 순서가 뒤집히면 "recent"를 장소 번호로 읽으려다 실패한다.
     */
    @Test
    void 최근_본_여행지를_돌려준다() throws Exception {
        RecentPlaceResult recent = new RecentPlaceResult();
        recent.setPlaceId(9L);
        recent.setPlaceName("경복궁");
        when(placeViewHistoryService.findRecent(42L, null)).thenReturn(List.of(recent));

        login();

        mockMvc.perform(get("/api/v1/places/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].placeId").value(9))
                .andExpect(jsonPath("$.data[0].placeName").value("경복궁"));

        verify(placeViewHistoryService).findRecent(42L, null);
    }

    @Test
    void 장소를_열면_최근_본_여행지에_남긴다() throws Exception {
        login();

        mockMvc.perform(post("/api/v1/places/9/view"))
                .andExpect(status().isOk());

        verify(placeViewHistoryService).record(42L, 9L);
    }

    /* 상세 화면은 로그인 없이도 열린다. 남길 곳이 없을 뿐 오류는 아니다. */
    @Test
    void 비로그인이_장소를_열면_기록하지_않고_넘어간다() throws Exception {
        mockMvc.perform(post("/api/v1/places/9/view"))
                .andExpect(status().isOk());

        verify(placeViewHistoryService, never()).record(any(), any());
    }
}
