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
