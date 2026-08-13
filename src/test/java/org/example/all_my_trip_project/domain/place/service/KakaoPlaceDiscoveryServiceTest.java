package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(kakaoClient.search(eq("Seoul cafe"), any())).thenReturn(List.of(discovered));
        when(placeDAO.upsert(discovered)).thenReturn(77L);
        when(placeDAO.findById(77L)).thenReturn(Optional.of(saved));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);
        when(placeRagService.toSearchResult(saved)).thenReturn(result);

        assertThat(service.discoverAndIndex("cafe", "Seoul")).containsExactly(result);

        verify(placeRagService).indexPlaces(List.of(saved));
    }

    @Test
    void continuesWhenOneKakaoPlaceCannotBeSaved() {
        PlaceDTO failed = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("failed").name("Failed place").build();
        PlaceDTO discovered = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("saved").name("Saved place").build();
        PlaceDTO saved = PlaceDTO.builder().placeId(88L).externalProvider("KAKAO").externalPlaceId("saved").name("Saved place").build();
        RagSearchResult result = new RagSearchResult("place:88", "Place name: Saved place");
        when(kakaoClient.search(eq("Seoul cafe"), any())).thenReturn(List.of(failed, discovered));
        when(placeDAO.upsert(failed)).thenThrow(new DataIntegrityViolationException("constraint"));
        when(placeDAO.upsert(discovered)).thenReturn(88L);
        when(placeDAO.findById(88L)).thenReturn(Optional.of(saved));
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
    void findsNearbyCafeNamesFromTheNamedAnchorPlace() {
        PlaceDTO anchor = PlaceDTO.builder()
                .externalProvider("KAKAO").externalPlaceId("anchor").name("이재모피자 본점")
                .longitude(new BigDecimal("129.030")).latitude(new BigDecimal("35.101")).build();
        PlaceDTO discovered = PlaceDTO.builder()
                .externalProvider("KAKAO").externalPlaceId("cafe-1").name("근처 실제 카페").build();
        PlaceDTO saved = PlaceDTO.builder()
                .placeId(99L).externalProvider("KAKAO").externalPlaceId("cafe-1").name("근처 실제 카페").build();
        RagSearchResult result = new RagSearchResult("place:99", "Place name: 근처 실제 카페");
        anchor.setAddress("부산 중구 광복중앙로");
        when(kakaoClient.search(eq("부산 이재모피자 본점"), any())).thenReturn(List.of(anchor));
        when(kakaoClient.searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any()))
                .thenReturn(List.of(discovered));
        when(placeDAO.upsert(discovered)).thenReturn(99L);
        when(placeDAO.findById(99L)).thenReturn(Optional.of(saved));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);
        when(placeRagService.toSearchResult(saved)).thenReturn(result);

        assertThat(service.discoverAndIndex("이재모피자 본점 근처 카페 추천해줘", "부산"))
                .contains(result);

        verify(kakaoClient).searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any());
    }

    @Test
    void choosesTheAnchorBranchThatMatchesTheTravelDestination() {
        PlaceDTO jejuBranch = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("jeju").name("이재모피자")
                .address("제주특별자치도 제주시 구남로").longitude(new BigDecimal("126.5")).latitude(new BigDecimal("33.5")).build();
        PlaceDTO busanBranch = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("busan").name("이재모피자")
                .address("부산광역시 중구 광복중앙로").longitude(new BigDecimal("129.0")).latitude(new BigDecimal("35.1")).build();
        PlaceDTO cafe = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("cafe").name("부산 실제 카페").build();
        PlaceDTO savedCafe = PlaceDTO.builder().placeId(51L).externalProvider("KAKAO").externalPlaceId("cafe").name("부산 실제 카페").build();
        when(kakaoClient.search(eq("부산 이재모피자"), any())).thenReturn(List.of(jejuBranch, busanBranch));
        when(kakaoClient.searchByCategory(eq("CE7"), eq(busanBranch.getLongitude()), eq(busanBranch.getLatitude()), any()))
                .thenReturn(List.of(cafe));
        when(placeDAO.upsert(cafe)).thenReturn(51L);
        when(placeDAO.findById(51L)).thenReturn(Optional.of(savedCafe));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);

        service.discoverAndIndex("이재모피자를 먹고 근처 카페 추천", "부산");

        verify(kakaoClient).searchByCategory(eq("CE7"), eq(busanBranch.getLongitude()), eq(busanBranch.getLatitude()), any());
    }

    @Test
    void usesTheAlreadyScheduledPlaceCoordinatesInsteadOfSearchingAnotherBranchByName() {
        PlaceDTO scheduledBusanBranch = PlaceDTO.builder().placeId(77L).name("이재모피자 본점")
                .longitude(new BigDecimal("129.030")).latitude(new BigDecimal("35.101")).build();
        PlaceDTO cafe = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("cafe-77").name("부산 실제 카페").build();
        PlaceDTO savedCafe = PlaceDTO.builder().placeId(88L).externalProvider("KAKAO").externalPlaceId("cafe-77").name("부산 실제 카페").build();
        when(placeDAO.findById(77L)).thenReturn(Optional.of(scheduledBusanBranch));
        when(kakaoClient.searchByCategory(eq("CE7"), eq(scheduledBusanBranch.getLongitude()),
                eq(scheduledBusanBranch.getLatitude()), any())).thenReturn(List.of(cafe));
        when(placeDAO.upsert(cafe)).thenReturn(88L);
        when(placeDAO.findById(88L)).thenReturn(Optional.of(savedCafe));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);

        service.discoverAndIndex("이재모피자 본점 근처 카페 추천", "부산", 77L);

        verify(kakaoClient).searchByCategory(eq("CE7"), eq(scheduledBusanBranch.getLongitude()),
                eq(scheduledBusanBranch.getLatitude()), any());
        verify(kakaoClient, org.mockito.Mockito.never()).search(eq("부산 이재모피자 본점"), any());
    }

    @Test
    void usesWalkingRadiusAndSortsNearbyCandidatesByDistance() {
        PlaceDTO anchor = PlaceDTO.builder().placeId(77L).name("Anchor")
                .longitude(new BigDecimal("129.0300")).latitude(new BigDecimal("35.1010")).build();
        PlaceDTO fartherCafe = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("far")
                .name("Far cafe").longitude(new BigDecimal("129.0350")).latitude(new BigDecimal("35.1010")).build();
        PlaceDTO nearerCafe = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("near")
                .name("Near cafe").longitude(new BigDecimal("129.0310")).latitude(new BigDecimal("35.1010")).build();
        PlaceDTO savedNearerCafe = PlaceDTO.builder().placeId(1L).externalProvider("KAKAO").externalPlaceId("near")
                .name("Near cafe").build();
        PlaceDTO savedFartherCafe = PlaceDTO.builder().placeId(2L).externalProvider("KAKAO").externalPlaceId("far")
                .name("Far cafe").build();
        when(placeDAO.findById(77L)).thenReturn(Optional.of(anchor));
        when(kakaoClient.searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any()))
                .thenReturn(List.of(fartherCafe, nearerCafe));
        when(placeDAO.upsert(nearerCafe)).thenReturn(1L);
        when(placeDAO.upsert(fartherCafe)).thenReturn(2L);
        when(placeDAO.findById(1L)).thenReturn(Optional.of(savedNearerCafe));
        when(placeDAO.findById(2L)).thenReturn(Optional.of(savedFartherCafe));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);

        service.discoverAndIndex("Anchor \uadfc\ucc98 \ub3c4\ubcf4 10\ubd84 \uc774\ub0b4 \uce74\ud398 \ucd94\ucc9c", "Busan", 77L);

        verify(kakaoClient).searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any());
        verify(placeRagService).indexPlaces(List.of(savedNearerCafe, savedFartherCafe));
    }

    @Test
    void doesNotExpandWalkingSearchBeyondTwoKilometersWhenNearbySearchIsEmpty() {
        PlaceDTO anchor = PlaceDTO.builder().placeId(77L).name("Anchor")
                .longitude(new BigDecimal("129.0300")).latitude(new BigDecimal("35.1010")).build();
        when(placeDAO.findById(77L)).thenReturn(Optional.of(anchor));
        when(kakaoClient.searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any()))
                .thenReturn(List.of());

        service.discoverAndIndex("Anchor \uadfc\ucc98 \ub3c4\ubcf4 \uce74\ud398 \ucd94\ucc9c", "Busan", 77L);

        verify(kakaoClient).searchByCategory(eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any());
        verify(kakaoClient, org.mockito.Mockito.never()).searchByCategory(
                eq("CE7"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), eq(5_000), any());
    }

    @Test
    void searchesShoppingByKeywordAroundTheAnchorInsteadOfUsingAttractionCategory() {
        PlaceDTO anchor = PlaceDTO.builder().placeId(77L).name("Anchor")
                .longitude(new BigDecimal("129.0300")).latitude(new BigDecimal("35.1010")).build();
        PlaceDTO shoppingPlace = PlaceDTO.builder().externalProvider("KAKAO").externalPlaceId("shop-1")
                .name("Verified shop").longitude(new BigDecimal("129.0310")).latitude(new BigDecimal("35.1010")).build();
        PlaceDTO savedShoppingPlace = PlaceDTO.builder().placeId(91L).externalProvider("KAKAO")
                .externalPlaceId("shop-1").name("Verified shop").build();
        when(placeDAO.findById(77L)).thenReturn(Optional.of(anchor));
        when(kakaoClient.searchNearby(eq("\uC1FC\uD551"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), eq(2_000), any()))
                .thenReturn(List.of(shoppingPlace));
        when(placeDAO.upsert(shoppingPlace)).thenReturn(91L);
        when(placeDAO.findById(91L)).thenReturn(Optional.of(savedShoppingPlace));
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);

        service.discoverAndIndex("Anchor \uadfc\ucc98 \uC1FC\uD551 \ucd94\ucc9c", "Busan", 77L);

        verify(kakaoClient).searchNearby(eq("\uC1FC\uD551"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), eq(2_000), any());
        verify(kakaoClient, org.mockito.Mockito.never()).searchByCategory(
                eq("AT4"), eq(anchor.getLongitude()), eq(anchor.getLatitude()), any());
    }

    @Test
    void removesLocationParticleFromNearbyAnchor() {
        assertThat(KakaoPlaceDiscoveryService.extractNearbyAnchor("\uC774\uC7AC\uBAA8\uD53C\uC790\uC5D0\uC11C \uADFC\uCC98 \uCE74\uD398 \uCD94\uCC9C"))
                .isEqualTo("\uC774\uC7AC\uBAA8\uD53C\uC790");
    }

    @Test
    void extractsNamedPlaceBeforeNearbyExpressionAsAnchor() {
        assertThat(KakaoPlaceDiscoveryService.extractNearbyAnchor("이재모피자 본점 근처에 유명 카페 추천해줘"))
                .isEqualTo("이재모피자 본점");
    }

    @Test
    void extractsVisitedPlaceBeforeNearbyExpressionAsAnchor() {
        assertThat(KakaoPlaceDiscoveryService.extractNearbyAnchor("1일차에 이재모피자를 먹고 근처에 카페를 가고 싶어"))
                .isEqualTo("이재모피자");
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
    void removesRequestedCountSoKakaoCanSearchTheActualPlaceType() {
        assertThat(KakaoPlaceDiscoveryService.searchKeyword("편집샵 세개만 추천해줘", "부산"))
                .isEqualTo("부산 편집샵");
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
    void extractsSpecificVenueTermsForKakaoPlaceSearch() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "부산에서 혼술 LP 바를 추천해줘", "부산"))
                .contains("부산 LP바", "부산 술집");
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

    @Test
    void limitsKakaoSearchKeywordsToProtectOverallResponseTime() {
        assertThat(KakaoPlaceDiscoveryService.searchKeywords(
                "성수 연남 이태원 강남 잠실 홍대 합정 여의도 카페 맛집 술집 쇼핑 관광지 추천해줘", "서울"))
                .hasSizeLessThanOrEqualTo(8);
    }
}
