package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.rag.dto.RagPlaceResult;

import java.util.List;

public interface AiModelClient {
    AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                             AiGuideContext context, List<RagPlaceResult> ragPlaces);
}
