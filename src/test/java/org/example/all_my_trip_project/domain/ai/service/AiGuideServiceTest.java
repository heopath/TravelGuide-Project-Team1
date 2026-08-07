package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.rag.dto.RagPlaceResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGuideServiceTest {
    private final AiModelClient aiModelClient = mock(AiModelClient.class);
    private final AiConversationHistoryService conversationHistoryService = mock(AiConversationHistoryService.class);
    private final AiGuideContextService contextService = mock(AiGuideContextService.class);
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider = mock(ObjectProvider.class);
    private final AiGuideService service = new AiGuideService(
            aiModelClient, conversationHistoryService, contextService, placeRagServiceProvider
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
        when(aiModelClient.generate(request, List.of(), context, List.of()))
                .thenThrow(new AiModelException("Gemini failed"));

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
    void addsRetrievedPlaceFactsToTheModelRequestWhenRagIsEnabled() {
        AiGuideRequest request = new AiGuideRequest("Recommend a beach cafe", 12L);
        AiGuideContext context = new AiGuideContext(null, List.of());
        PlaceRagService placeRagService = mock(PlaceRagService.class);
        List<RagPlaceResult> ragPlaces = List.of(
                new RagPlaceResult(7L, "Beach Cafe", "CAFE", "Busan", "Beach road", "Ocean view cafe")
        );
        when(placeRagServiceProvider.getIfAvailable()).thenReturn(placeRagService);
        when(placeRagService.search(request.question())).thenReturn(ragPlaces);
        when(conversationHistoryService.load(1L, 12L)).thenReturn(List.of());
        when(contextService.load(1L, request)).thenReturn(context);
        when(aiModelClient.generate(request, List.of(), context, ragPlaces))
                .thenReturn(new AiGuideResponse("answer", List.of(), List.of(), List.of()));

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, List.of(), context, ragPlaces);
    }
}
