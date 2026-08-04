package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGuideServiceTest {

    private final AiModelClient aiModelClient = mock(AiModelClient.class);
    private final AiConversationHistoryService conversationHistoryService = mock(AiConversationHistoryService.class);
    private final AiGuideService service = new AiGuideService(aiModelClient, conversationHistoryService);

    @Test
    void sendsUsersRecentHistoryToModelAndStoresSuccessfulResponse() {
        AiGuideRequest request = new AiGuideRequest("음식점도 두 곳 추가해줘");
        List<AiConversationTurn> history = List.of(new AiConversationTurn("광안리 카페 추천", "카페 두 곳을 추천합니다."));
        AiGuideResponse response = new AiGuideResponse("음식점 두 곳을 추가합니다.", List.of(), List.of(), List.of());
        when(conversationHistoryService.load(1L)).thenReturn(history);
        when(aiModelClient.generate(request, history)).thenReturn(response);

        service.generate(request, false, 1L);

        verify(aiModelClient).generate(request, history);
        verify(conversationHistoryService).append(1L, request.question(), response.answer());
    }

    @Test
    void doesNotStoreConversationWhenAiGenerationFails() {
        AiGuideRequest request = new AiGuideRequest("오류 발생 테스트");
        when(conversationHistoryService.load(1L)).thenReturn(List.of());
        when(aiModelClient.generate(request, List.of())).thenThrow(new AiModelException("Gemini failed"));

        assertThatThrownBy(() -> service.generate(request, false, 1L))
                .isInstanceOf(AiModelException.class);

        verify(conversationHistoryService, never()).append(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
