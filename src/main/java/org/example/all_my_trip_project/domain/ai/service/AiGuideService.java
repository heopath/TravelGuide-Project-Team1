package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiGuideService {
    private final AiModelClient aiModelClient;
    private final AiConversationHistoryService conversationHistoryService;
    private final AiGuideContextService contextService;
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider;

    @Value("${ai.guide.mock.enabled:false}")
    private boolean mockEnabled;

    public AiGuideResponse generate(AiGuideRequest request, boolean simulateServerError, Long userId) {
        if (mockEnabled && simulateServerError) {
            throw new IllegalStateException("AI mock server error");
        }
        List<AiConversationTurn> history = conversationHistoryService.load(userId, request.tripId());
        List<RagSearchResult> ragResults = loadRagResults(request.question());
        AiGuideResponse response = aiModelClient.generate(
                request, history, contextService.load(userId, request), ragResults);
        conversationHistoryService.append(userId, request.tripId(), request.question(), response.answer());
        return response;
    }

    private List<RagSearchResult> loadRagResults(String question) {
        PlaceRagService service = placeRagServiceProvider.getIfAvailable();
        return service == null ? List.of() : service.search(question);
    }
}
