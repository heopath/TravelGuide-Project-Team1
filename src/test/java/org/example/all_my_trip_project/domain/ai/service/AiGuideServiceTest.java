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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(aiModelClient.generate(request, List.of(), context, List.of())).thenThrow(new AiModelException("Cohere failed"));

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
        when(ragService.search(request.question())).thenReturn(List.of(indexed));
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(request.question(), "Seoul")).thenReturn(List.of(discovered));
        when(aiModelClient.generate(request, List.of(), context, List.of(discovered, indexed))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(request.question(), "Seoul");
        verify(aiModelClient).generate(request, List.of(), context, List.of(discovered, indexed));
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

        assertThat(actual.days().getFirst().items()).satisfiesExactly(
                item -> {
                    assertThat(item.placeId()).isEqualTo(25L);
                    assertThat(item.placeCategory()).isEqualTo("CAFE");
                    assertThat(item.placeAddress()).isEqualTo("서울 성동구 성수동");
                    assertThat(item.placeUrl()).isEqualTo("https://place.map.kakao.com/25");
                },
                item -> assertThat(item.placeId()).isNull()
        );
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
    void usesOnlyScheduledAnchorCandidatesEvenWithoutNearbyWording() {
        AiGuideRequest request = new AiGuideRequest("1일차에 그리다부부에서 커피를 마신 후 구경할 수 있는 곳을 추천해줘", 12L);
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
        when(aiModelClient.generate(request, List.of(), context, List.of(nearbyAttraction))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(), context, List.of(nearbyAttraction));
    }

    @Test
    void searchesAgainFromThePreviousNearbyQuestionAndExcludesAlreadySuggestedPlaces() {
        AiGuideRequest request = new AiGuideRequest("다른 곳 추천해줘", 12L);
        AiConversationTurn previousTurn = new AiConversationTurn(
                "이재모피자 본점 근처 카페 추천해줘", "레드버튼 남포점을 추천합니다.");
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
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.search(previousTurn.question())).thenReturn(List.of());
        when(kakaoPlaceDiscoveryServiceProvider.getIfAvailable()).thenReturn(discoveryService);
        when(discoveryService.discoverAndIndex(previousTurn.question(), null))
                .thenReturn(List.of(redButton, anotherCafe));
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of(anotherCafe))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(previousTurn.question(), null);
        verify(aiModelClient).generate(request, List.of(previousTurn), context, List.of(anotherCafe));
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
        when(discoveryService.discoverAndIndex(expectedSearchQuestion, null)).thenReturn(List.of(restaurant));
        when(aiModelClient.generate(request, List.of(previousTurn), context, List.of(restaurant))).thenReturn(response);

        service.generate(request, false, 1L);

        verify(discoveryService).discoverAndIndex(expectedSearchQuestion, null);
        verify(aiModelClient).generate(request, List.of(previousTurn), context, List.of(restaurant));
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
}
