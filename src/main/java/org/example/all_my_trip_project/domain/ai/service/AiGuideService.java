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
import java.util.HashSet;
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
        String placeSearchQuestion = resolvePlaceSearchQuestion(request.question(), history);
        List<RagSearchResult> ragResults = loadRagResults(placeSearchQuestion, context);
        ragResults = excludePreviouslySuggestedPlaces(ragResults, history, request.question());
        AiGuideResponse response = aiModelClient.generate(
                request, history, context, ragResults);
        response = enrichVerifiedPlaces(response, ragResults, request.question());
        conversationHistoryService.append(userId, request.tripId(), request.question(), response.answer());
        return response;
    }

    public void resetConversation(Long userId, Long tripId) {
        conversationHistoryService.reset(userId, tripId);
    }

    private String resolvePlaceSearchQuestion(String question, List<AiConversationTurn> history) {
        if (!isAlternativeRequest(question) || history == null || history.isEmpty()) {
            return question;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            String previousQuestion = history.get(index).question();
            String anchor = KakaoPlaceDiscoveryService.extractNearbyAnchor(previousQuestion);
            if (!anchor.isBlank()) {
                if (containsVenueCondition(question)) {
                    return anchor + " 근처 " + question;
                }
                return previousQuestion;
            }
        }
        return question;
    }

    /**
     * "다른 곳" 요청은 직전 장소를 기준으로 다시 찾되, 사용자가 카페·식당처럼
     * 업종을 새로 지정했다면 그 조건을 우선 적용한다.
     */
    private boolean containsVenueCondition(String question) {
        String value = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (value.contains("\uCE74\uD398") || value.contains("\uCEE4\uD53C") || value.contains("\uC2DD\uB2F9")
                || value.contains("\uB9DB\uC9D1") || value.contains("\uC220\uC9D1") || value.contains("\uBC14")
                || value.contains("\uC1FC\uD551") || value.contains("\uD3B8\uC9D1\uC0F5") || value.contains("\uAD00\uAD11")) {
            return true;
        }
        return value.contains("카페") || value.contains("커피") || value.contains("식당")
                || value.contains("맛집") || value.contains("술집") || value.contains("바")
                || value.contains("쇼핑") || value.contains("편집샵") || value.contains("관광");
    }

    private List<RagSearchResult> excludePreviouslySuggestedPlaces(List<RagSearchResult> places,
                                                                     List<AiConversationTurn> history,
                                                                     String question) {
        if (!isAlternativeRequest(question) || places == null || places.isEmpty() || history == null) {
            return places;
        }
        String previousAnswers = history.stream()
                .map(AiConversationTurn::answer)
                .filter(answer -> answer != null)
                .map(this::normalizePlaceName)
                .collect(java.util.stream.Collectors.joining(" "));
        if (previousAnswers.isBlank()) {
            return places;
        }
        return places.stream()
                .filter(place -> place.placeName() == null
                        || !previousAnswers.contains(normalizePlaceName(place.placeName())))
                .toList();
    }

    private boolean isAlternativeRequest(String question) {
        String normalized = normalizePlaceName(question);
        String raw = question == null ? "" : question;
        if (raw.contains("\uB2E4\uB978") || raw.contains("\uB9D0\uACE0")
                || raw.contains("\uC7AC\uCD94\uCC9C") || raw.contains("\uB2E4\uC2DC\uCD94\uCC9C")) {
            return true;
        }
        return normalized.contains("다른곳") || normalized.contains("다른데")
                || normalized.contains("말고") || normalized.contains("또추천") || normalized.contains("다시추천");
    }

    private AiGuideResponse enrichVerifiedPlaces(AiGuideResponse response, List<RagSearchResult> ragResults,
                                                 String question) {
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
        List<RagSearchResult> verifiedPlaces = ragResults.stream()
                .filter(result -> result.placeId() != null && result.placeName() != null && !result.placeName().isBlank())
                .toList();
        HashSet<Long> usedPlaceIds = new HashSet<>();
        List<AiGuideDayResponse> days = response.days().stream()
                .map(day -> new AiGuideDayResponse(day.day(), day.title(), day.items().stream()
                        .map(item -> {
                            RagSearchResult exactPlace = uniquePlacesByName.get(normalizePlaceName(item.name()));
                            RagSearchResult fallbackPlace = exactPlace == null
                                    ? findVerifiedFallback(item, question, verifiedPlaces, usedPlaceIds)
                                    : exactPlace;
                            if (fallbackPlace != null && fallbackPlace.placeId() != null) {
                                usedPlaceIds.add(fallbackPlace.placeId());
                            }
                            return toVerifiedItem(item, fallbackPlace,
                                    exactPlace == null && fallbackPlace != null && isFallbackEligibleItem(item));
                        })
                        .toList()))
                .toList();
        return new AiGuideResponse(response.answer(), days, response.externalLinks(), response.sources());
    }

    private RagSearchResult findVerifiedFallback(AiGuideItemResponse item, String question,
                                                  List<RagSearchResult> places, HashSet<Long> usedPlaceIds) {
        if (!isFallbackEligibleItem(item)) {
            return null;
        }
        String expectedCategory = expectedCategoryForFallback(item, question);
        return places.stream()
                .filter(place -> !usedPlaceIds.contains(place.placeId()))
                .filter(place -> expectedCategory == null || expectedCategory.equals(place.category()))
                .findFirst()
                .orElse(null);
    }

    private boolean isFallbackEligibleItem(AiGuideItemResponse item) {
        String name = normalizePlaceName(item.name());
        return isGenericPlaceItem(item)
                || name.contains("\uCE74\uD398\uD0D0\uBC29") || name.contains("\uB9DB\uC9D1\uD0D0\uBC29")
                || name.contains("\uC810\uC2EC\uC2DD\uC0AC") || name.contains("\uC800\uB141\uC2DD\uC0AC")
                || name.contains("\uC1FC\uD551") || name.contains("\uD328\uC158\uAC70\uB9AC")
                || name.contains("\uAD00\uAD11") || name.contains("\uC0B0\uCC45")
                || name.contains("\uB3C4\uBCF4\uD0D0\uBC29") || name.contains("\uAD6C\uACBD")
                || name.equals("\uCE74\uD398") || name.equals("\uB9DB\uC9D1") || name.equals("\uC2DD\uB2F9");
    }

    private String expectedCategoryForFallback(AiGuideItemResponse item, String question) {
        String value = (String.valueOf(item.name()) + " " + String.valueOf(item.reason()) + " "
                + String.valueOf(question)).toLowerCase(Locale.ROOT);
        if (value.contains("\uCE74\uD398") || value.contains("\uCEE4\uD53C")) {
            return "CAFE";
        }
        if (value.contains("\uB9DB\uC9D1") || value.contains("\uC2DD\uB2F9") || value.contains("\uC810\uC2EC")
                || value.contains("\uC800\uB141") || value.contains("\uC74C\uC2DD")) {
            return "RESTAURANT";
        }
        if (value.contains("\uC1FC\uD551") || value.contains("\uD328\uC158") || value.contains("\uD3B8\uC9D1\uC0F5")
                || value.contains("\uAD00\uAD11") || value.contains("\uC0B0\uCC45") || value.contains("\uB3C4\uBCF4")
                || value.contains("\uAD6C\uACBD")) {
            return "ATTRACTION";
        }
        return expectedCategory(item, question);
    }

    private boolean isGenericPlaceItem(AiGuideItemResponse item) {
        String name = normalizePlaceName(item.name());
        return name.contains("카페탐방") || name.contains("맛집탐방") || name.contains("점심식사")
                || name.contains("저녁식사") || name.equals("카페") || name.equals("맛집") || name.equals("식당");
    }

    private String expectedCategory(AiGuideItemResponse item, String question) {
        String value = (String.valueOf(item.name()) + " " + String.valueOf(item.reason()) + " "
                + String.valueOf(question)).toLowerCase(Locale.ROOT);
        if (value.contains("카페") || value.contains("커피")) {
            return "CAFE";
        }
        if (value.contains("맛집") || value.contains("식당") || value.contains("점심") || value.contains("저녁")
                || value.contains("음식")) {
            return "RESTAURANT";
        }
        return null;
    }

    private AiGuideItemResponse toVerifiedItem(AiGuideItemResponse item, RagSearchResult place, boolean replaceGenericName) {
        if (place == null) {
            return new AiGuideItemResponse(item.time(), item.name(), item.reason());
        }
        String name = replaceGenericName ? place.placeName() : item.name();
        return new AiGuideItemResponse(item.time(), name, item.reason(), place.placeId(),
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
        Long scheduledAnchorPlaceId = findScheduledAnchorPlaceId(question, context);
        List<RagSearchResult> discoveredResults = scheduledAnchorPlaceId == null
                ? discoveryService.discoverAndIndex(question, destination)
                : discoveryService.discoverAndIndex(question, destination, scheduledAnchorPlaceId);

        // 기준 장소 "근처" 추천은 방금 카카오에서 찾은 주변 장소만 사용한다.
        // 이전 검색으로 색인된 다른 지역 후보가 섞이면 잘못된 상호명이 추천될 수 있다.
        // An existing itinerary place is an equally reliable local-search anchor even when
        // the user says "after visiting X" instead of explicitly saying "near X".
        // In that case, never merge stale RAG candidates from unrelated regions.
        if (scheduledAnchorPlaceId != null || !KakaoPlaceDiscoveryService.extractNearbyAnchor(question).isBlank()) {
            return discoveredResults;
        }

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

    private Long findScheduledAnchorPlaceId(String question, org.example.all_my_trip_project.domain.ai.dto.AiGuideContext context) {
        if (question == null || context == null || context.trip() == null) {
            return null;
        }
        String normalizedQuestion = normalizePlaceName(question);
        return context.trip().days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.placeId() != null && item.title() != null && !item.title().isBlank())
                .filter(item -> matchesQuestionPlace(normalizedQuestion, normalizePlaceName(item.title())))
                .map(org.example.all_my_trip_project.domain.ai.dto.AiGuideContext.Item::placeId)
                .findFirst()
                .orElse(null);
    }

    private boolean matchesQuestionPlace(String normalizedQuestion, String normalizedTitle) {
        if (normalizedTitle.isBlank()) {
            return false;
        }
        String withoutBranchSuffix = normalizedTitle.replaceAll("(본점|점)$", "");
        return normalizedQuestion.contains(normalizedTitle)
                || (!withoutBranchSuffix.isBlank() && normalizedQuestion.contains(withoutBranchSuffix));
    }
}
