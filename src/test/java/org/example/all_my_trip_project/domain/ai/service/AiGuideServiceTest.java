package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.example.all_my_trip_project.domain.place.service.KakaoPlaceDiscoveryService;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;

import java.util.List;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

class AiGuideServiceTest {
    private final AiModelClient aiModelClient = mock(AiModelClient.class);
    private final AiConversationHistoryService conversationHistoryService = mock(AiConversationHistoryService.class);
    private final AiGuideContextService contextService = mock(AiGuideContextService.class);
    private final ObjectProvider<org.example.all_my_trip_project.domain.rag.service.PlaceRagService> ragServiceProvider = mock(ObjectProvider.class);
    private final ObjectProvider<KakaoPlaceDiscoveryService> kakaoPlaceDiscoveryServiceProvider = mock(ObjectProvider.class);
    private final AiGuideService service = new AiGuideService(
            aiModelClient, conversationHistoryService, contextService, ragServiceProvider,
            kakaoPlaceDiscoveryServiceProvider
    );

    @Test
    void searchesExpandedKakaoCandidatesForAlternativeRequests() {
        AiGuideRequest request = new AiGuideRequest("다른 식당도 추천해줘", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiConversationTurn previous = new AiConversationTurn("서귀포 점심 식당 추천", "이전 추천\n[추천 장소] 기존 식당");
        RagSearchResult alreadySuggested = new RagSearchResult("place:1", "first", 1L,
                "기존 식당", "RESTAURANT", "제주 서귀포시", "https://place.map.kakao.com/1");
        RagSearchResult alternative = new RagSearchResult("place:2", "second", 2L,
                "새 식당", "RESTAURANT", "제주 서귀포시", "https://place.map.kakao.com/2");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("12:00", "새 식당", "점심 추천", 2L,
                        "RESTAURANT", "제주 서귀포시", "https://place.map.kakao.com/2")))), List.of(), List.of());
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previous));
        when(contextService.load(1L, request)).thenReturn(context);
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAlternativeAndIndex(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(alreadySuggested, alternative));
        when(aiModelClient.generate(request, List.of(previous), context, List.of(alternative))).thenReturn(response);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items().getFirst().name()).isEqualTo("새 식당");
        verify(discoveryService).discoverAlternativeAndIndex(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        verify(discoveryService, never()).discoverAndIndex(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void excludesOnlyTheImmediatelyPreviousRecommendationForAlternativeRequests() {
        AiGuideRequest request = new AiGuideRequest("다른 식당 추천해줘", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiConversationTurn older = new AiConversationTurn("서귀포 식당 추천", "[추천 장소] 첫 식당");
        AiConversationTurn latest = new AiConversationTurn("다른 식당 추천", "[추천 장소] 직전 식당");
        RagSearchResult olderPlace = new RagSearchResult("place:1", "older", 1L,
                "첫 식당", "RESTAURANT", "제주 서귀포시", "");
        RagSearchResult latestPlace = new RagSearchResult("place:2", "latest", 2L,
                "직전 식당", "RESTAURANT", "제주 서귀포시", "");
        RagSearchResult freshPlace = new RagSearchResult("place:3", "fresh", 3L,
                "새 식당", "RESTAURANT", "제주 서귀포시", "");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(older, latest));
        when(contextService.load(1L, request)).thenReturn(context);
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAlternativeAndIndex(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(olderPlace, latestPlace, freshPlace));
        when(aiModelClient.generate(request, List.of(older, latest), context, List.of(olderPlace, freshPlace)))
                .thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(older, latest), context, List.of(olderPlace, freshPlace));
    }

    @Test
    void keepsTheReferencedVerifiedPlaceCardWhenRequestingAnotherTime() {
        AiGuideRequest request = new AiGuideRequest(
                "DAY 3의 서울명예도로 끼리끼리3길을 다른 시간대로 추천해줘", 12L, 65L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 3, 65L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult referencePlace = new RagSearchResult("place:65", "verified", 65L,
                "서울명예도로 끼리끼리3길", "ATTRACTION", "서울 마포구 연남동 255-30",
                "https://place.map.kakao.com/894873893");
        AiGuideResponse modelResponse = new AiGuideResponse("16:00~18:00에 추천합니다.",
                List.of(new AiGuideDayResponse(3, "DAY 3 시간 조정", List.of(
                        new AiGuideItemResponse("16:00", "야간 산책", "기존 일정과 겹치지 않는 시간입니다.")))),
                List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(
                org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(ragService.findByPlaceId(65L)).thenReturn(Optional.of(referencePlace));
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(referencePlace))).thenReturn(modelResponse);

        AiGuideItemResponse item = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(item.time()).isEqualTo("16:00");
        assertThat(item.name()).isEqualTo("서울명예도로 끼리끼리3길");
        assertThat(item.placeId()).isEqualTo(65L);
        assertThat(item.placeUrl()).isEqualTo("https://place.map.kakao.com/894873893");
    }

    @Test
    void sendsRecentHistoryAndContextToModelAndStoresSuccessfulResponse() {
        AiGuideRequest request = new AiGuideRequest("Add two restaurants", 12L);
        List<AiConversationTurn> history = List.of(new AiConversationTurn("Recommend a cafe", "Try a cafe nearby"));
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("Added restaurants", List.of(), List.of(), List.of());
        when(conversationHistoryService.load(1L, 12L)).thenReturn(history);
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, history, context, List.of())).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, history, context, List.of());
        verify(conversationHistoryService).append(1L, 12L, request.question(), response.answer());
    }

    @Test
    void doesNotStoreConversationWhenAiGenerationFails() {
        AiGuideRequest request = new AiGuideRequest("Failure test", null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        when(conversationHistoryService.load(1L, null)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenThrow(new AiModelException("AI provider failed"));

        assertThatThrownBy(() -> service.generate(request, false, 1L))
                .isInstanceOf(AiModelException.class);

        verify(conversationHistoryService, never()).append(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void discoversVerifiedKakaoPlacesWhenRagHasNoCandidate() {
        AiGuideRequest request = new AiGuideRequest("Seongsu cafe", 12L);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "Seoul trip", "Seoul", null, null,
                        null, null, null, null, null, null, null, null, null, List.of()),
                List.of()
        );
        AiGuideResponse response = new AiGuideResponse("Cafe result", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        List<RagSearchResult> discovered = List.of(new RagSearchResult("place:99", "Place name: Real Cafe"));

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "Seoul")).thenReturn(discovered);
        when(aiModelClient.generate(request, List.of(), context, discovered)).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "Seoul");
        verify(aiModelClient).generate(request, List.of(), context, discovered);
    }

    @Test
    void supplementsExistingRagCandidatesWithFreshKakaoPlaces() {
        AiGuideRequest request = new AiGuideRequest("Seongsu restaurant", 12L);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "Seoul trip", "Seoul", null, null,
                        null, null, null, null, null, null, null, null, null, List.of()),
                List.of()
        );
        AiGuideResponse response = new AiGuideResponse("Restaurant result", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult indexed = new RagSearchResult("place:1", "Place name: Existing cafe");
        RagSearchResult discovered = new RagSearchResult("place:2", "Place name: Real restaurant");

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "Seoul")).thenReturn(List.of(discovered));
        when(aiModelClient.generate(request, List.of(), context, List.of(discovered))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "Seoul");
        verify(ragService, org.mockito.Mockito.never()).search(request.question());
        verify(aiModelClient).generate(request, List.of(), context, List.of(discovered));
    }

    @Test
    void addsCardMetadataOnlyWhenAiNamesAVerifiedPlace() {
        AiGuideRequest request = new AiGuideRequest("성수 카페", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verified = new RagSearchResult("place:25", "verified", 25L,
                "실제 카페", "CAFE", "서울 성동구 성수동", "https://place.map.kakao.com/25");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("10:00", "실제 카페", "검증된 카페"),
                        new AiGuideItemResponse("13:00", "점심 식사", "일반 안내")
                ))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verified));
        when(aiModelClient.generate(request, List.of(), context, List.of(verified))).thenReturn(response);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items()).singleElement().satisfies(item -> {
            assertThat(item.placeId()).isEqualTo(25L);
            assertThat(item.placeCategory()).isEqualTo("CAFE");
            assertThat(item.placeAddress()).isEqualTo("서울 성동구 성수동");
            assertThat(item.placeUrl()).isEqualTo("https://place.map.kakao.com/25");
        });
    }

    @Test
    void usesFreshKakaoCandidatesWhenRagIsDisabled() {
        AiGuideRequest request = new AiGuideRequest("부산 해운대해수욕장 추천해줘", 12L);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null, List.of()),
                List.of());
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult beach = new RagSearchResult("place:101", "verified", 101L,
                "해운대해수욕장", "ATTRACTION", "부산 해운대구", "https://place.map.kakao.com/101");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("14:00", "관광", "해수욕장을 둘러보세요")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(null);
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산")).thenReturn(List.of(beach));
        when(aiModelClient.generate(request, List.of(), context, List.of(beach))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.name()).isEqualTo("해운대해수욕장");
        assertThat(actual.placeId()).isEqualTo(101L);
        assertThat(actual.placeCategory()).isEqualTo("ATTRACTION");
    }

    @Test
    void replacesLocationPrefixedGenericCafeWithVerifiedKakaoPlace() {
        AiGuideRequest request = new AiGuideRequest("성수동 실제 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedCafe = new RagSearchResult("place:301", "verified", 301L,
                "실제 성수 카페", "CAFE", "서울 성동구 성수동", "https://place.map.kakao.com/301");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("10:00", "성수동 카페", "커피와 휴식을 위한 방문")))),
                List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedCafe));
        when(aiModelClient.generate(request, List.of(), context, List.of(verifiedCafe))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.name()).isEqualTo("실제 성수 카페");
        assertThat(actual.placeId()).isEqualTo(301L);
        assertThat(actual.placeAddress()).isEqualTo("서울 성동구 성수동");
    }

    @Test
    void hidesLocationPrefixedGenericCardWhenNoVerifiedPlaceExists() {
        AiGuideRequest request = new AiGuideRequest("성수동 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("10:00", "성수동 카페", "커피와 휴식을 위한 방문")))),
                List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(response);

        assertThat(service.generate(request, false, 1L).days()).isEmpty();
    }

    @Test
    void hidesUnverifiedCafeCardWhenNoVerifiedPlaceExists() {
        AiGuideRequest request = new AiGuideRequest("이재모피자 근처 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("14:00", "카페 (미확인)", "주변 카페를 탐방해 보세요.")))),
                List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(
                org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(response);

        assertThat(service.generate(request, false, 1L).days()).isEmpty();
    }

    @Test
    void replacesGenericCultureAndStreetFoodItemsWithVerifiedPlaces() {
        AiGuideRequest request = new AiGuideRequest("성수에서 문화 공간과 길거리 음식을 추천해줘", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult attraction = new RagSearchResult("place:201", "verified", 201L,
                "실제 전시 공간", "ATTRACTION", "서울 성동구", "https://place.map.kakao.com/201");
        RagSearchResult restaurant = new RagSearchResult("place:202", "verified", 202L,
                "실제 분식집", "RESTAURANT", "서울 성동구", "https://place.map.kakao.com/202");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("14:00", "서울 문화 시설 탐방", "전시를 즐기세요"),
                        new AiGuideItemResponse("16:00", "길거리 음식 탐방", "간단한 음식을 드세요")
                ))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(attraction, restaurant));
        when(aiModelClient.generate(request, List.of(), context, List.of(attraction, restaurant))).thenReturn(response);

        List<AiGuideItemResponse> items = service.generate(request, false, 1L).days().getFirst().items();

        assertThat(items).extracting(AiGuideItemResponse::name)
                .containsExactly("실제 전시 공간", "실제 분식집");
        assertThat(items).extracting(AiGuideItemResponse::placeId).containsExactly(201L, 202L);
    }

    @Test
    void replacesGenericCafeItemWithAnUnusedVerifiedCafe() {
        AiGuideRequest request = new AiGuideRequest("이재모피자 근처 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedCafe = new RagSearchResult("place:41", "verified", 41L,
                "실제 카페", "CAFE", "부산 중구 광복중앙로", "https://place.map.kakao.com/41");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("14:00", "카페 탐방", "근처 카페를 둘러보세요")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedCafe));
        when(aiModelClient.generate(request, List.of(), context, List.of(verifiedCafe))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.name()).isEqualTo("실제 카페");
        assertThat(actual.placeId()).isEqualTo(41L);
        assertThat(actual.placeAddress()).isEqualTo("부산 중구 광복중앙로");
    }

    @Test
    void matchesRestaurantAndCafeCardsToTheirOwnCategoriesWhenQuestionRequestsBoth() {
        AiGuideRequest request = new AiGuideRequest("성수 점심 식당과 카페를 추천해줘", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult restaurant = new RagSearchResult("place:51", "verified", 51L,
                "실제 식당", "RESTAURANT", "서울 성동구", "https://place.map.kakao.com/51");
        RagSearchResult cafe = new RagSearchResult("place:52", "verified", 52L,
                "실제 카페", "CAFE", "서울 성동구", "https://place.map.kakao.com/52");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("12:00", "점심 식사", "근처 맛집에서 식사하세요"),
                        new AiGuideItemResponse("14:00", "카페 탐방", "커피를 마시며 쉬세요")
                ))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(restaurant, cafe));
        when(aiModelClient.generate(request, List.of(), context, List.of(restaurant, cafe))).thenReturn(response);

        List<AiGuideItemResponse> items = service.generate(request, false, 1L).days().getFirst().items();

        assertThat(items).extracting(AiGuideItemResponse::name)
                .containsExactly("실제 식당", "실제 카페");
        assertThat(items).extracting(AiGuideItemResponse::placeCategory)
                .containsExactly("RESTAURANT", "CAFE");
    }

    @Test
    void replacesGenericShoppingItemWithAnUnusedVerifiedAttraction() {
        AiGuideRequest request = new AiGuideRequest("\uad11\ubcf5\ub85c\uc5d0\uc11c \uad6c\uacbd\ud560 \uacf3\uc744 \ucd94\ucc9c\ud574\uc918", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedAttraction = new RagSearchResult("place:63", "verified", 63L,
                "\uad11\ubcf5\ub85c \ud328\uc158\uac70\ub9ac", "ATTRACTION", "\ubd80\uc0b0 \uc911\uad6c \uad11\ubcf5\ub85c", "https://place.map.kakao.com/63");
        AiGuideResponse response = new AiGuideResponse("\ucd94\ucc9c", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("15:30", "\uad11\ubcf5\ub85c \ud328\uc158 \uac70\ub9ac \uc1fc\ud551", "\uc1fc\ud551\uacfc \uad6c\uacbd\uc744 \uc990\uae30\uc138\uc694")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedAttraction));
        when(aiModelClient.generate(request, List.of(), context, List.of(verifiedAttraction))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.name()).isEqualTo("\uad11\ubcf5\ub85c \ud328\uc158\uac70\ub9ac");
        assertThat(actual.placeId()).isEqualTo(63L);
        assertThat(actual.placeCategory()).isEqualTo("ATTRACTION");
    }

    @Test
    void replacesUnverifiedMuseumItemWithAVerifiedNearbyAttractionCard() {
        AiGuideRequest request = new AiGuideRequest("DAY 2 빈 시간에 근처 박물관 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 2, null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedMuseum = new RagSearchResult("place:64", "verified", 64L,
                "경찰박물관", "ATTRACTION", "서울 종로구", "https://place.map.kakao.com/64");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(2, "DAY 2",
                List.of(new AiGuideItemResponse("14:00", "국립기상박물관", "박물관 관람")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService =
                mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedMuseum));
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(verifiedMuseum))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.name()).isEqualTo("경찰박물관");
        assertThat(actual.placeId()).isEqualTo(64L);
        assertThat(actual.placeUrl()).isEqualTo("https://place.map.kakao.com/64");
    }

    @Test
    void correctsRequestedDayAndReplacesGenericTrailWithVerifiedAttractionCard() {
        AiGuideRequest request = new AiGuideRequest("DAY 3 보고 일정 중간에 넣을만한 일정 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 3, null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedTrail = new RagSearchResult("place:65", "verified", 65L,
                "연남동 경의선숲길", "ATTRACTION", "서울 마포구", "https://place.map.kakao.com/65");
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(4, "DAY 4 오후 산책",
                List.of(new AiGuideItemResponse("14:00", "경의선숲길", "산책과 휴식을 위한 2시간 코스")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService =
                mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedTrail));
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(verifiedTrail))).thenReturn(modelResponse);

        AiGuideDayResponse actualDay = service.generate(request, false, 1L).days().getFirst();
        AiGuideItemResponse actualItem = actualDay.items().getFirst();

        assertThat(actualDay.day()).isEqualTo(3);
        assertThat(actualDay.title()).isEqualTo("DAY 3 오후 산책");
        assertThat(actualItem.name()).isEqualTo("연남동 경의선숲길");
        assertThat(actualItem.placeId()).isEqualTo(65L);
        assertThat(actualItem.placeUrl()).isEqualTo("https://place.map.kakao.com/65");
    }

    @Test
    void keepsSelectedDayWhenQuestionContainsCalendarDate() {
        AiGuideRequest request = new AiGuideRequest("8월 19일 저녁 맛집 추천", 12L, 2, null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult verifiedRestaurant = new RagSearchResult("place:66", "verified", 66L,
                "실제 저녁 식당", "RESTAURANT", "서울 종로구", "https://place.map.kakao.com/66");
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1 저녁",
                List.of(new AiGuideItemResponse("18:00", "실제 저녁 식당", "저녁 식사 추천")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(
                org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(verifiedRestaurant));
        when(aiModelClient.generate(request, List.of(), context, List.of(verifiedRestaurant))).thenReturn(modelResponse);

        AiGuideDayResponse actualDay = service.generate(request, false, 1L).days().getFirst();

        assertThat(actualDay.day()).isEqualTo(2);
        assertThat(actualDay.title()).isEqualTo("DAY 2 저녁");
    }

    @Test
    void prefersAnExplicitDayInTheQuestionOverTheCurrentlySelectedDay() {
        AiGuideRequest request = new AiGuideRequest("DAY 2 전포 점심 식당 추천해줘", 12L, 1, null);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 2, null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1 점심",
                List.of(new AiGuideItemResponse("12:00", "전포 실제 식당", "점심 식사 추천")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of())).thenReturn(modelResponse);

        AiGuideDayResponse actualDay = service.generate(request, false, 1L).days().getFirst();

        assertThat(actualDay.day()).isEqualTo(2);
        assertThat(actualDay.title()).isEqualTo("DAY 2 점심");
        verify(aiModelClient).generate(effectiveRequest, List.of(), context, List.of());
    }

    @Test
    void recognizesKoreanOrdinalDayExpressionOverTheCurrentlySelectedDay() {
        AiGuideRequest request = new AiGuideRequest("둘째 날에 서귀포 점심 식당 추천해줘", 12L, 1, null);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 2, null);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1 점심",
                List.of(new AiGuideItemResponse("12:00", "서귀포 실제 식당", "점심 식사 추천")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of())).thenReturn(modelResponse);

        AiGuideDayResponse actualDay = service.generate(request, false, 1L).days().getFirst();

        assertThat(actualDay.day()).isEqualTo(2);
        assertThat(actualDay.title()).isEqualTo("DAY 2 점심");
        verify(aiModelClient).generate(effectiveRequest, List.of(), context, List.of());
    }

    @Test
    void usesOnlyFreshNearbyCandidatesSoOldRagCandidatesDoNotLeakIntoTheRecommendation() {
        AiGuideRequest request = new AiGuideRequest("이재모피자 근처 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult oldIndexedPlace = new RagSearchResult("place:1", "old", 1L,
                "서울 카페", "CAFE", "서울", "https://place.map.kakao.com/1");
        RagSearchResult nearbyPlace = new RagSearchResult("place:2", "nearby", 2L,
                "부산 카페", "CAFE", "부산 중구", "https://place.map.kakao.com/2");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(oldIndexedPlace));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), null)).thenReturn(List.of(nearbyPlace));
        when(aiModelClient.generate(request, List.of(), context, List.of(nearbyPlace))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(), context, List.of(nearbyPlace));
    }

    @Test
    void usesTheScheduledPlacesIdAsTheNearbySearchAnchor() {
        AiGuideRequest request = new AiGuideRequest("이재모피자 본점 근처 카페 추천", 12L);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(77L, "이재모피자 본점", null, null, "FOOD", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult nearbyCafe = new RagSearchResult("place:88", "nearby", 88L,
                "부산 카페", "CAFE", "부산 중구", "https://place.map.kakao.com/88");

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산", 77L)).thenReturn(List.of(nearbyCafe));
        when(aiModelClient.generate(request, List.of(), context, List.of(nearbyCafe))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "부산", 77L);
    }

    @Test
    void usesSelectedDaysLastScheduledPlaceAsTheNearbySearchAnchorWithoutDayWording() {
        AiGuideRequest request = new AiGuideRequest("점심 먹고 뭐할지 추천해줘", 12L, 2, null);
        AiGuideContext.Item dayOneItem = new AiGuideContext.Item(71L, "DAY 1 식당", LocalTime.of(12, 0), null, "FOOD", null);
        AiGuideContext.Item dayTwoLunch = new AiGuideContext.Item(88L, "DAY 2 점심 식당", LocalTime.of(12, 30), null, "FOOD", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "서울 여행", "서울", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(
                                new AiGuideContext.Day(1, null, "DAY 1", null, List.of(dayOneItem)),
                                new AiGuideContext.Day(2, null, "DAY 2", null, List.of(dayTwoLunch))
                        )),
                List.of());
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult nearbyAttraction = new RagSearchResult("place:89", "nearby", 89L,
                "실제 서울 명소", "ATTRACTION", "서울", "https://place.map.kakao.com/89");

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "서울", 88L)).thenReturn(List.of(nearbyAttraction));
        when(aiModelClient.generate(request, List.of(), context, List.of(nearbyAttraction))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "서울", 88L);
        verify(aiModelClient).generate(request, List.of(), context, List.of(nearbyAttraction));
    }

    @Test
    void usesTheRequestedDayInsteadOfAGenericRestaurantNameAsTheNearbySearchAnchor() {
        AiGuideRequest request = new AiGuideRequest("DAY 2에 식당을 갔다가 근처에 할 수 있는게 뭐가 있어?", 12L);
        AiGuideContext.Item dayOneRestaurant = new AiGuideContext.Item(71L, "식당", LocalTime.of(12, 0), null, "FOOD", null);
        AiGuideContext.Item dayTwoRestaurant = new AiGuideContext.Item(88L, "식당", LocalTime.of(12, 30), null, "FOOD", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "서울 여행", "서울", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(
                                new AiGuideContext.Day(1, null, "DAY 1", null, List.of(dayOneRestaurant)),
                                new AiGuideContext.Day(2, null, "DAY 2", null, List.of(dayTwoRestaurant))
                        )),
                List.of()
        );
        RagSearchResult nearbyAttraction = new RagSearchResult("place:99", "candidate", 99L,
                "경희궁", "ATTRACTION", "서울 종로구", "https://place.map.kakao.com/99");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(
                new AiGuideDayResponse(2, "DAY 2", List.of(
                        new AiGuideItemResponse("15:00", "경희궁", "추천", 99L, null, null, null)
                ))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService =
                mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "서울", 88L)).thenReturn(List.of(nearbyAttraction));
        when(aiModelClient.generate(request, List.of(), context, List.of(nearbyAttraction))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "서울", 88L);
        verify(discoveryService, org.mockito.Mockito.never()).discoverAndIndex(request.question(), "서울", 71L);
    }

    @Test
    void doesNotUseIndexedCandidatesWhenScheduledAnchorDiscoveryFindsNoFreshPlace() {
        AiGuideRequest request = new AiGuideRequest("\uC774\uC7AC\uBAA8\uD53C\uC790 \uB2E4\uC74C \uCF54\uC2A4 \uCD94\uCC9C", 12L);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(77L, "\uC774\uC7AC\uBAA8\uD53C\uC790", null, null, "FOOD", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "\uBD80\uC0B0 \uC5EC\uD589", "\uBD80\uC0B0", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        RagSearchResult indexed = new RagSearchResult("place:88", "indexed", 88L,
                "\uADFC\uCC98 \uCE74\uD398", "CAFE", "\uBD80\uC0B0 \uC911\uAD6C", "https://place.map.kakao.com/88");
        AiGuideResponse response = new AiGuideResponse("\uCD94\uCC9C", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(indexed));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "\uBD80\uC0B0", 77L)).thenReturn(List.of());
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(), context, List.of());
    }

    @Test
    void usesOnlyDestinationMatchedIndexedCandidatesWhenDaySearchFindsNoFreshPlace() {
        AiGuideRequest request = new AiGuideRequest("DAY 1 부산 점심 식당 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 1, null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산광역시", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of()))),
                List.of());
        RagSearchResult busanRestaurant = new RagSearchResult("place:88", "장소명: 부산 식당\n주소: 부산광역시 중구",
                88L, "부산 식당", "RESTAURANT", "부산광역시 중구", "https://place.map.kakao.com/88");
        RagSearchResult otherRegionRestaurant = new RagSearchResult("place:99", "장소명: 서울 식당\n주소: 서울특별시 종로구",
                99L, "서울 식당", "RESTAURANT", "서울특별시 종로구", "https://place.map.kakao.com/99");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(busanRestaurant, otherRegionRestaurant));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산광역시")).thenReturn(List.of());
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(busanRestaurant))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(effectiveRequest, List.of(), context, List.of(busanRestaurant));
    }

    @Test
    void excludesIndexedAttractionWhenShoppingIsRequested() {
        AiGuideRequest request = new AiGuideRequest("DAY 1 전포에서 쇼핑할 곳 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 1, null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산광역시", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of()))),
                List.of());
        RagSearchResult attraction = new RagSearchResult("place:201",
                "장소명: 전포성당\n카테고리: 관광·명소\n주소: 부산 부산진구 서전로38번길54",
                201L, "전포성당", "ATTRACTION", "부산 부산진구 서전로38번길54",
                "https://place.map.kakao.com/201");
        RagSearchResult shoppingPlace = new RagSearchResult("place:202",
                "장소명: 전포 소품샵\n카테고리: 쇼핑\n주소: 부산 부산진구",
                202L, "전포 소품샵", "SHOPPING", "부산 부산진구",
                "https://place.map.kakao.com/202");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(attraction, shoppingPlace));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산광역시")).thenReturn(List.of());
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(shoppingPlace))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(effectiveRequest, List.of(), context, List.of(shoppingPlace));
    }

    @Test
    void usesOnlyScheduledAnchorCandidatesEvenWithoutNearbyWording() {
        AiGuideRequest request = new AiGuideRequest("1일차에 그리다부부에서 커피를 마신 후 구경할 수 있는 곳을 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 1, null);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(77L, "그리다부부", null, null, "CAFE", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        RagSearchResult staleOtherRegionPlace = new RagSearchResult("place:1", "indexed", 1L,
                "다른 지역 카페", "CAFE", "전남", "https://place.map.kakao.com/1");
        RagSearchResult nearbyAttraction = new RagSearchResult("place:88", "nearby", 88L,
                "부산 실제 명소", "ATTRACTION", "부산 중구", "https://place.map.kakao.com/88");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(staleOtherRegionPlace));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산", 77L)).thenReturn(List.of(nearbyAttraction));
        when(aiModelClient.generate(effectiveRequest, List.of(), context, List.of(nearbyAttraction))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(effectiveRequest, List.of(), context, List.of(nearbyAttraction));
    }

    @Test
    void searchesAgainFromThePreviousNearbyQuestionAndExcludesAlreadySuggestedPlaces() {
        AiGuideRequest request = new AiGuideRequest("다른 곳 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "이재모피자 본점 근처 카페 추천해줘", "추천 일정을 준비했어요.\n[추천 장소] 레드버튼 남포점");
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult redButton = new RagSearchResult("place:1", "candidate", 1L,
                "레드버튼 남포점", "CAFE", "부산", "https://place.map.kakao.com/1");
        RagSearchResult anotherCafe = new RagSearchResult("place:2", "candidate", 2L,
                "다른 부산 카페", "CAFE", "부산", "https://place.map.kakao.com/2");
        AiGuideResponse response = new AiGuideResponse("다른 카페", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAlternativeAndIndex(previousTurn.question(), null, null))
                .thenReturn(List.of(redButton, anotherCafe));
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of(anotherCafe))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAlternativeAndIndex(previousTurn.question(), null, null);
        verify(aiModelClient).generate(request, List.of(previousTurn), context, List.of(anotherCafe));
    }

    @Test
    void keepsPreviouslySuggestedPlaceWhenUserRequestsAnotherTimeForIt() {
        AiGuideRequest request = new AiGuideRequest(
                "DAY 3의 서울명예도로 끼리끼리3길을 현재 일정과 겹치지 않는 다른 시간대로 추천해줘", 12L);
        AiGuideRequest effectiveRequest = new AiGuideRequest(request.question(), 12L, 3, null);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "DAY 3 보고 일정 중간에 넣을만한 일정 추천해줘",
                "추천 일정을 준비했어요.\n[추천 장소] 서울명예도로 끼리끼리3길");
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult previousPlace = new RagSearchResult("place:71", "candidate", 71L,
                "서울명예도로 끼리끼리3길", "ATTRACTION", "서울 마포구", "https://place.map.kakao.com/71");
        AiGuideResponse response = new AiGuideResponse("시간을 조정했습니다.", List.of(new AiGuideDayResponse(3, "DAY 3",
                List.of(new AiGuideItemResponse("20:00", "서울명예도로 끼리끼리3길", "야간 산책")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService =
                mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(previousPlace));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), null)).thenReturn(List.of(previousPlace));
        when(aiModelClient.generate(effectiveRequest, List.of(previousTurn), context, List.of(previousPlace))).thenReturn(response);

        AiGuideItemResponse actual = service.generate(request, false, 1L).days().getFirst().items().getFirst();

        assertThat(actual.placeId()).isEqualTo(71L);
        assertThat(actual.placeUrl()).isEqualTo("https://place.map.kakao.com/71");
        verify(discoveryService).discoverAndIndex(request.question(), null);
    }

    @Test
    void excludesPlacesAlreadySavedInTheCurrentTripFromRecommendationCandidates() {
        AiGuideRequest request = new AiGuideRequest("근처 카페 추천", 12L);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(
                77L, "이미 저장된 카페", null, null, "CAFE", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        RagSearchResult alreadySavedFromKakao = new RagSearchResult("kakao:77", "candidate", 77L,
                "이미 저장된 카페", "CAFE", "부산 중구", "https://place.map.kakao.com/77");
        RagSearchResult alreadySavedFromRagByName = new RagSearchResult("rag:99", "indexed", 99L,
                "이미 저장된 카페", "CAFE", "부산 중구", "https://place.map.kakao.com/99");
        RagSearchResult alternative = new RagSearchResult("place:88", "candidate", 88L,
                "새로운 카페", "CAFE", "부산 중구", "https://place.map.kakao.com/88");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(alreadySavedFromRagByName));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "부산"))
                .thenReturn(List.of(alreadySavedFromKakao, alternative));
        when(aiModelClient.generate(request, List.of(), context, List.of(alternative))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(), context, List.of(alternative));
    }

    @Test
    void keepsThePreviousPlaceAnchorButUsesTheNewVenueConditionForAlternativeRequests() {
        AiGuideRequest request = new AiGuideRequest("다른 식당 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "이재모피자 본점 근처 카페 추천해줘", "근처 카페를 추천합니다.");
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("다른 식당", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult restaurant = new RagSearchResult("place:3", "candidate", 3L,
                "부산 식당", "RESTAURANT", "부산", "https://place.map.kakao.com/3");
        String expectedSearchQuestion = "이재모피자 본점 근처 다른 식당 추천해줘";

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(expectedSearchQuestion)).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAlternativeAndIndex(expectedSearchQuestion, null, null))
                .thenReturn(List.of(restaurant));
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of(restaurant))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAlternativeAndIndex(expectedSearchQuestion, null, null);
        verify(aiModelClient).generate(request, List.of(previousTurn), context, List.of(restaurant));
    }

    @Test
    void keepsThePreviousTravelAreaWhenRequestingAnotherRestaurant() {
        AiGuideRequest request = new AiGuideRequest("다른 식당 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "첫날은 서귀포로 갈 것 같은데 점심 식당 맛집 추천해줘", "서귀포 식당을 추천합니다.");
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("다른 식당", List.of(), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);
        KakaoPlaceDiscoveryService discoveryService = mock(KakaoPlaceDiscoveryService.class);
        RagSearchResult restaurant = new RagSearchResult("place:4", "candidate", 4L,
                "서귀포 다른 식당", "RESTAURANT", "서귀포", "https://place.map.kakao.com/4");
        String expectedSearchQuestion = "서귀포 다른 식당 추천해줘";

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(expectedSearchQuestion)).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAlternativeAndIndex(expectedSearchQuestion, null, null))
                .thenReturn(List.of(restaurant));
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of(restaurant))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAlternativeAndIndex(expectedSearchQuestion, null, null);
    }

    @Test
    void doesNotAttachPlaceMetadataWhenSeveralVerifiedPlacesHaveTheSameName() {
        AiGuideRequest request = new AiGuideRequest("스타벅스 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        RagSearchResult firstBranch = new RagSearchResult("place:25", "verified", 25L,
                "스타벅스", "CAFE", "서울 성동구 성수동", "https://place.map.kakao.com/25");
        RagSearchResult secondBranch = new RagSearchResult("place:26", "verified", 26L,
                "스타벅스", "CAFE", "서울 강남구 역삼동", "https://place.map.kakao.com/26");
        AiGuideResponse response = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("10:00", "스타벅스", "카페 추천")))), List.of(), List.of());
        org.example.all_my_trip_project.domain.rag.service.PlaceRagService ragService = mock(org.example.all_my_trip_project.domain.rag.service.PlaceRagService.class);

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(request.question())).thenReturn(List.of(firstBranch, secondBranch));
        when(aiModelClient.generate(request, List.of(), context, List.of(firstBranch, secondBranch))).thenReturn(response);

        AiGuideResponse actual = service.generate(request, false, 1L);

        AiGuideItemResponse item = actual.days().getFirst().items().getFirst();
        assertThat(item.placeId()).isNull();
        assertThat(item.placeCategory()).isNull();
        assertThat(item.placeAddress()).isNull();
        assertThat(item.placeUrl()).isNull();
    }

    @Test
    void removesAnAlreadyScheduledPlaceReturnedDirectlyByTheModel() {
        AiGuideRequest request = new AiGuideRequest("근처 카페 추천", 12L);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(
                77L, "이미 저장된 카페", null, null, "CAFE", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("12:00", "이미 저장된 카페", "모델이 잘못 재추천", 77L,
                                "CAFE", "부산", "https://place.map.kakao.com/77"),
                        new AiGuideItemResponse("14:00", "새로운 카페", "새 후보")
                ))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(modelResponse);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items())
                .extracting(AiGuideItemResponse::name)
                .containsExactly("새로운 카페");
    }

    @Test
    void returnsGeneralGuidanceWhenAllModelRecommendationsAreAlreadyScheduled() {
        AiGuideRequest request = new AiGuideRequest("근처 카페 추천", 12L);
        AiGuideContext.Item scheduledItem = new AiGuideContext.Item(
                77L, "이미 저장된 카페", null, null, "CAFE", null);
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "부산 여행", "부산", null, null,
                        null, null, null, null, null, null, null, null, null,
                        List.of(new AiGuideContext.Day(1, null, "DAY 1", null, List.of(scheduledItem)))),
                List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("12:00", "이미 저장된 카페", "모델이 잘못 재추천", 77L,
                        "CAFE", "부산", "https://place.map.kakao.com/77")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(modelResponse);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days()).isEmpty();
        assertThat(actual.answer()).contains("새로운 장소를 제안하지 못했어요");
    }

    @Test
    void removesAPlaceReturnedAgainForAnAlternativeRequestEvenWhenTheModelIgnoresHistory() {
        AiGuideRequest request = new AiGuideRequest("다른 곳 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "이재모피자 본점 근처 카페 추천", "추천 일정을 준비했어요.\n[추천 장소] 레드버튼 남포점");
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("다른 추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("14:00", "레드버튼 남포점", "모델이 이전 답변을 무시함"),
                        new AiGuideItemResponse("15:00", "새로운 카페", "새 후보")
                ))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of())).thenReturn(modelResponse);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items())
                .extracting(AiGuideItemResponse::name)
                .containsExactly("새로운 카페");
    }

    @Test
    void keepsPlaceWhoseNameOnlyPartiallyMatchesPreviousRecommendation() {
        AiGuideRequest request = new AiGuideRequest("다른 곳 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "성수 카페 추천", "추천 일정을 준비했어요.\n[추천 장소] 성수");
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("다른 추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("15:00", "성수커피하우스", "새 후보")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of())).thenReturn(modelResponse);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items())
                .extracting(AiGuideItemResponse::name)
                .containsExactly("성수커피하우스");
    }

    @Test
    void storesRecommendedCardPlaceNamesInConversationHistory() {
        AiGuideRequest request = new AiGuideRequest("근처 카페 추천", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse response = new AiGuideResponse("추천 일정을 준비했어요.", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("14:00", "레드버튼 남포점", "카페 추천")))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenReturn(response);

        service.generate(request, false, 1L);

        verify(conversationHistoryService).append(eq(1L), eq(12L), eq(request.question()),
                contains("[추천 장소] 레드버튼 남포점"));
    }

    @Test
    void removesAPlaceThatWasOnlyInThePreviousRecommendationCard() {
        AiGuideRequest request = new AiGuideRequest("다른 곳 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "이재모피자 본점 근처 카페 추천", "추천 일정을 준비했어요.\n[추천 장소] 레드버튼 남포점");
        AiGuideContext context = new AiGuideContext(null, List.of());
        AiGuideResponse modelResponse = new AiGuideResponse("다른 추천", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(
                        new AiGuideItemResponse("14:00", "레드버튼 남포점", "카드에만 있던 이전 추천"),
                        new AiGuideItemResponse("15:00", "새로운 카페", "새 후보")
                ))), List.of(), List.of());

        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of(previousTurn));
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of())).thenReturn(modelResponse);

        AiGuideResponse actual = service.generate(request, false, 1L);

        assertThat(actual.days().getFirst().items())
                .extracting(AiGuideItemResponse::name)
                .containsExactly("새로운 카페");
    }
}
