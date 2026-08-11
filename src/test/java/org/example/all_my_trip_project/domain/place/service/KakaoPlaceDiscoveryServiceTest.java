package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoPlaceDiscoveryServiceTest {

    private final KakaoLocalPlaceClient kakaoClient = mock(KakaoLocalPlaceClient.class);
    private final PlaceDAO placeDAO = mock(PlaceDAO.class);
    private final PlaceRagService placeRagService = mock(PlaceRagService.class);
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider = mock(ObjectProvider.class);
    private final KakaoPlaceDiscoveryService service = new KakaoPlaceDiscoveryService(
            kakaoClient, placeDAO, placeRagServiceProvider
    );

    @Test
    void savesAndIndexesDiscoveredKakaoPlaces() {
        PlaceDTO discovered = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("123").name("Real Cafe").build();
        PlaceDTO saved = PlaceDTO.builder().placeId(77L).externalProvider("KAKAO").externalPlaceId("123").name("Real Cafe").build();
        RagSearchResult result = new RagSearchResult("place:77", "Place name: Real Cafe");
        when(kakaoClient.search("Seoul cafe")).thenReturn(List.of(discovered));
        when(placeDAO.upsert(discovered)).thenReturn(77L);
        when(placeDAO.findById(77L)).thenReturn(Optional.of(saved));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);
        when(placeRagService.toSearchResult(saved)).thenReturn(result);

        assertThat(service.discoverAndIndex("cafe", "Seoul")).containsExactly(result);

        verify(placeRagService).indexPlaces(List.of(saved));
    }

    @Test
    void keepsQuestionWhenItAlreadyContainsDestination() {
        assertThat(KakaoPlaceDiscoveryService.searchKeyword("Seoul cafe", "Seoul"))
                .isEqualTo("Seoul cafe");
    }

    @Test
    void removesRecommendationWordsBeforeKakaoKeywordSearch() {
        assertThat(KakaoPlaceDiscoveryService.searchKeyword("성수동 카페 추천해줘", "서울특별시"))
                .isEqualTo("서울특별시 성수동 카페");
    }

    @Test
    void usesDestinationWhenQuestionOnlyContainsRecommendationPhrase() {
        assertThat(KakaoPlaceDiscoveryService.searchKeyword("맛집 추천해줘", "부산"))
                .isEqualTo("부산 맛집");
    }

    @Test
    void extractsCafeAndRestaurantSearchesForEachMentionedLocation() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "성수와 연남에서 카페와 맛집을 추천해줘", "서울"))
                .contains("성수 카페", "성수 맛집", "연남 카페", "연남 맛집");
    }

    @Test
    void extractsAnyDistrictAndNightlifeIntentWithoutHardCodedLocations() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "전주 객사에서 밥집과 유흥거리를 추천해줘", "전주"))
                .contains("객사 맛집", "객사 술집");
    }

    @Test
    void extractsStationAndShoppingIntent() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "대전역 근처 쇼핑할 곳을 찾아줘", "대전"))
                .contains("대전역 쇼핑");
    }

    @Test
    void givesEveryDayItsOwnVerifiedPlaceSearches() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "첫 날은 성수, 둘째 날은 연남, 셋째 날은 이태원에서 혼술 바, 넷째 날은 강남에서 쇼핑하고 점심 저녁 맛집을 추천해줘",
                "서울"))
                .containsExactlyInAnyOrder(
                        "성수 맛집", "연남 맛집", "이태원 맛집", "이태원 술집", "강남 맛집", "강남 쇼핑"
                );
    }
}
