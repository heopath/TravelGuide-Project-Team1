package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.example.all_my_trip_project.domain.place.service.KakaoPlaceDiscoveryService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AiGuideService {
    private final AiModelClient aiModelClient;
    private final AiConversationHistoryService conversationHistoryService;
    private final AiGuideContextService contextService;
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider;
    private final ObjectProvider<KakaoPlaceDiscoveryService> kakaoPlaceDiscoveryServiceProvider;

    @Value("${ai.guide.mock.enabled:false}")
    private boolean mockEnabled;

    public AiGuideResponse generate(AiGuideRequest request, boolean simulateServerError, Long userId) {
        if (mockEnabled && simulateServerError) {
            throw new IllegalStateException("AI mock server error");
        }
        List<AiConversationTurn> history = conversationHistoryService.load(userId, request.tripId());
        var context = contextService.load(userId, request);
        List<RagSearchResult> ragResults = loadRagResults(request.question(), context);
        AiGuideResponse response = aiModelClient.generate(
                request, history, context, ragResults);
        response = enrichVerifiedPlaces(response, ragResults);
        conversationHistoryService.append(userId, request.tripId(), request.question(), response.answer());
        return response;
    }

    private AiGuideResponse enrichVerifiedPlaces(AiGuideResponse response, List<RagSearchResult> ragResults) {
        if (response == null || response.days() == null || ragResults == null || ragResults.isEmpty()) {
            return response;
        }
        Map<String, List<RagSearchResult>> candidatesByName = ragResults.stream()
                .filter(result -> result.placeId() != null && result.placeName() != null && !result.placeName().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        result -> normalizePlaceName(result.placeName()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<String, RagSearchResult> uniquePlacesByName = candidatesByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getFirst(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<AiGuideDayResponse> days = response.days().stream()
                .map(day -> new AiGuideDayResponse(day.day(), day.title(), day.items().stream()
                        .map(item -> toVerifiedItem(item, uniquePlacesByName.get(normalizePlaceName(item.name()))))
                        .toList()))
                .toList();
        return new AiGuideResponse(response.answer(), days, response.externalLinks(), response.sources());
    }

    private AiGuideItemResponse toVerifiedItem(AiGuideItemResponse item, RagSearchResult place) {
        if (place == null) {
            return new AiGuideItemResponse(item.time(), item.name(), item.reason());
        }
        return new AiGuideItemResponse(item.time(), item.name(), item.reason(), place.placeId(),
                place.category(), place.address(), place.placeUrl());
    }

    private String normalizePlaceName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    private List<RagSearchResult> loadRagResults(String question, org.example.all_my_trip_project.domain.ai.dto.AiGuideContext context) {
        PlaceRagService service = placeRagServiceProvider.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        List<RagSearchResult> indexedResults = service.search(question);
        KakaoPlaceDiscoveryService discoveryService = kakaoPlaceDiscoveryServiceProvider.getIfAvailable();
        if (discoveryService == null) {
            return indexedResults;
        }
        String destination = context == null || context.trip() == null ? null : context.trip().destinationName();
        List<RagSearchResult> discoveredResults = discoveryService.discoverAndIndex(question, destination);

        // A previous cafe search must not prevent a later restaurant/place search.
        // Prefer the fresh Kakao candidates, then supplement them with indexed candidates.
        return Stream.concat(discoveredResults.stream(), indexedResults.stream())
                .collect(java.util.stream.Collectors.toMap(
                        RagSearchResult::source,
                        result -> result,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream()
                .toList();
    }
}
