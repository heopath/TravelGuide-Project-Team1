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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        RagSearchResult referencePlace = loadReferencePlace(request.referencePlaceId());
        ragResults = includeReferencePlace(ragResults, referencePlace);
        ragResults = excludeScheduledPlaces(ragResults, context);
        ragResults = excludePreviouslySuggestedPlaces(ragResults, history, request.question());
        AiGuideResponse response = aiModelClient.generate(
                request, history, context, ragResults);
        response = alignDaysWithRequestedDay(response, request.question());
        response = attachReferencePlaceToTimeAdjustment(response, request.question(), referencePlace);
        response = excludeFinalResponsePlaces(response, context, history, request.question());
        response = removeUnverifiedGenericItems(enrichVerifiedPlaces(response, ragResults, request.question()));
        conversationHistoryService.append(userId, request.tripId(), request.question(), toConversationAnswer(response));
        return response;
    }

    private RagSearchResult loadReferencePlace(Long referencePlaceId) {
        if (referencePlaceId == null) {
            return null;
        }
        PlaceRagService service = placeRagServiceProvider.getIfAvailable();
        return service == null ? null : service.findByPlaceId(referencePlaceId).orElse(null);
    }

    private List<RagSearchResult> includeReferencePlace(List<RagSearchResult> ragResults,
                                                          RagSearchResult referencePlace) {
        if (referencePlace == null || referencePlace.placeId() == null) {
            return ragResults;
        }
        return Stream.concat(Stream.of(referencePlace), ragResults == null ? Stream.empty() : ragResults.stream())
                .collect(java.util.stream.Collectors.toMap(
                        RagSearchResult::source,
                        result -> result,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream()
                .toList();
    }

    /**
     * "다른 시간 추천"은 새 장소를 제안하는 요청이 아니다. 화면에서 보낸 placeId의
     * 실제 장소 정보를 그대로 유지해야 지도 보기와 일정 추가를 다시 제공할 수 있다.
     */
    private AiGuideResponse attachReferencePlaceToTimeAdjustment(AiGuideResponse response,
                                                                   String question,
                                                                   RagSearchResult referencePlace) {
        if (!isTimeAdjustmentRequest(question) || referencePlace == null || referencePlace.placeId() == null
                || response == null || response.days() == null) {
            return response;
        }
        List<AiGuideDayResponse> days = response.days().stream()
                .filter(day -> day != null && day.items() != null)
                .map(day -> new AiGuideDayResponse(day.day(), day.title(), day.items().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(item -> new AiGuideItemResponse(
                                item.time(), referencePlace.placeName(), item.reason(), referencePlace.placeId(),
                                referencePlace.category(), referencePlace.address(), referencePlace.placeUrl()))
                        .toList()))
                .filter(day -> !day.items().isEmpty())
                .toList();
        return new AiGuideResponse(response.answer(), days, response.externalLinks(), response.sources());
    }

    public void resetConversation(Long userId, Long tripId) {
        conversationHistoryService.reset(userId, tripId);
    }

    private String resolvePlaceSearchQuestion(String question, List<AiConversationTurn> history) {
        if (!isAlternativeRequest(question) || isTimeAdjustmentRequest(question) || history == null || history.isEmpty()) {
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
        if (!isAlternativeRequest(question) || isTimeAdjustmentRequest(question)
                || places == null || places.isEmpty() || history == null) {
            return places;
        }
        Set<String> previousSuggestedPlaceNames = extractPreviouslySuggestedPlaceNames(history);
        if (previousSuggestedPlaceNames.isEmpty()) {
            return places;
        }
        return places.stream()
                .filter(place -> place.placeName() == null
                        || !previousSuggestedPlaceNames.contains(normalizePlaceName(place.placeName())))
                .toList();
    }

    /**
     * 일정에 이미 저장된 장소는 추천 후보에서 제외한다. 기준 장소로 사용된 장소는
     * 주변 검색의 앵커로만 쓰며, 결과 카드로 다시 추천하지 않는다.
     */
    private List<RagSearchResult> excludeScheduledPlaces(List<RagSearchResult> places,
                                                          org.example.all_my_trip_project.domain.ai.dto.AiGuideContext context) {
        if (places == null || places.isEmpty() || context == null || context.trip() == null) {
            return places;
        }

        Set<Long> scheduledPlaceIds = context.trip().days().stream()
                .filter(day -> day != null && day.items() != null)
                .flatMap(day -> day.items().stream())
                .map(org.example.all_my_trip_project.domain.ai.dto.AiGuideContext.Item::placeId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> scheduledPlaceNames = context.trip().days().stream()
                .filter(day -> day != null && day.items() != null)
                .flatMap(day -> day.items().stream())
                .map(org.example.all_my_trip_project.domain.ai.dto.AiGuideContext.Item::title)
                .filter(title -> title != null && !title.isBlank())
                .map(this::normalizePlaceName)
                .collect(java.util.stream.Collectors.toSet());

        if (scheduledPlaceIds.isEmpty() && scheduledPlaceNames.isEmpty()) {
            return places;
        }
        return places.stream()
                .filter(place -> place.placeId() == null || !scheduledPlaceIds.contains(place.placeId()))
                .filter(place -> place.placeName() == null
                        || !scheduledPlaceNames.contains(normalizePlaceName(place.placeName())))
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

    /**
     * "다른 시간 추천"은 새 장소를 찾는 요청이 아니라 방금 추천한 장소의
     * 시간만 바꾸는 요청이다. 이 경우에는 이전 추천 장소 제외 규칙을 적용하지 않는다.
     */
    private boolean isTimeAdjustmentRequest(String question) {
        String normalized = normalizePlaceName(question);
        return normalized.contains("다른시간") || normalized.contains("시간조정")
                || normalized.contains("시간변경") || normalized.contains("시간대로")
                || normalized.contains("시간대추천") || normalized.contains("시간을바꿔");
    }

    /**
     * 모델이 여행 문맥이나 이전 답변에서 본 장소를 다시 반환하더라도, 화면으로 보내기 전에
     * 마지막으로 제외한다. 후보 목록과 프롬프트는 보조 수단이며, 이 검사가 최종 안전장치다.
     */
    private AiGuideResponse excludeFinalResponsePlaces(AiGuideResponse response,
                                                        org.example.all_my_trip_project.domain.ai.dto.AiGuideContext context,
                                                        List<AiConversationTurn> history,
                                                        String question) {
        if (response == null || response.days() == null) {
            return response;
        }

        Set<Long> scheduledPlaceIds = new HashSet<>();
        Set<String> scheduledPlaceNames = new HashSet<>();
        if (context != null && context.trip() != null) {
            context.trip().days().stream()
                    .filter(day -> day != null && day.items() != null)
                    .flatMap(day -> day.items().stream())
                    .forEach(item -> {
                        if (item.placeId() != null) {
                            scheduledPlaceIds.add(item.placeId());
                        }
                        if (item.title() != null && !item.title().isBlank()) {
                            scheduledPlaceNames.add(normalizePlaceName(item.title()));
                        }
                    });
        }

        Set<String> previousSuggestedPlaceNames = isAlternativeRequest(question)
                && !isTimeAdjustmentRequest(question) && history != null
                ? extractPreviouslySuggestedPlaceNames(history)
                : Set.of();

        if (scheduledPlaceIds.isEmpty() && scheduledPlaceNames.isEmpty() && previousSuggestedPlaceNames.isEmpty()) {
            return response;
        }

        List<AiGuideDayResponse> filteredDays = response.days().stream()
                .filter(day -> day != null && day.items() != null)
                .map(day -> new AiGuideDayResponse(day.day(), day.title(), day.items().stream()
                        .filter(item -> !isExcludedFinalItem(item, scheduledPlaceIds, scheduledPlaceNames,
                                previousSuggestedPlaceNames))
                        .toList()))
                .filter(day -> !day.items().isEmpty())
                .toList();
        if (filteredDays.isEmpty()) {
            return new AiGuideResponse(
                    "이미 일정에 있거나 최근에 추천한 장소와 겹쳐 새로운 장소를 제안하지 못했어요. "
                            + "지역, 업종 또는 원하는 시간대를 조금 다르게 알려주시면 다시 찾아볼게요.",
                    List.of(), response.externalLinks(), response.sources());
        }
        return new AiGuideResponse(response.answer(), filteredDays, response.externalLinks(), response.sources());
    }

    private boolean isExcludedFinalItem(AiGuideItemResponse item,
                                        Set<Long> scheduledPlaceIds,
                                        Set<String> scheduledPlaceNames,
                                        Set<String> previousSuggestedPlaceNames) {
        if (item.placeId() != null && scheduledPlaceIds.contains(item.placeId())) {
            return true;
        }
        String normalizedName = normalizePlaceName(item.name());
        if (scheduledPlaceNames.contains(normalizedName)) {
            return true;
        }
        return previousSuggestedPlaceNames.contains(normalizedName);
    }

    private Set<String> extractPreviouslySuggestedPlaceNames(List<AiConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return Set.of();
        }
        return history.stream()
                .map(AiConversationTurn::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .flatMap(answer -> Stream.of(answer.split("\\R")))
                .map(String::trim)
                .filter(line -> line.startsWith("[추천 장소]"))
                .flatMap(line -> Stream.of(line.substring("[추천 장소]".length()).split(",")))
                .map(this::normalizePlaceName)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 다음 "다른 곳 추천" 요청에서 카드에만 표시된 상호명도 제외할 수 있도록
     * 모델의 설명 문장과 최종 카드 장소명을 함께 대화 이력에 남긴다.
     */
    private String toConversationAnswer(AiGuideResponse response) {
        if (response == null) {
            return "";
        }
        String answer = response.answer() == null ? "" : response.answer();
        if (response.days() == null) {
            return answer;
        }

        Map<String, String> placeNamesByNormalizedName = new LinkedHashMap<>();
        response.days().stream()
                .filter(day -> day != null && day.items() != null)
                .flatMap(day -> day.items().stream())
                .map(AiGuideItemResponse::name)
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> placeNamesByNormalizedName.putIfAbsent(normalizePlaceName(name), name));

        if (placeNamesByNormalizedName.isEmpty()) {
            return answer;
        }
        return answer + "\n[추천 장소] " + String.join(", ", placeNamesByNormalizedName.values());
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
                .filter(day -> day != null && day.items() != null)
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

    /**
     * 일정에 바로 추가할 수 없는 "지역 식당", "카페 휴식" 같은 일반 문구는
     * 실제 장소 추천 카드로 보여주지 않는다. 검증된 placeId가 있는 장소·관광지만
     * 일정 카드에 남겨 사용자가 다른 지점을 실수로 저장하지 않게 한다.
     */
    private AiGuideResponse removeUnverifiedGenericItems(AiGuideResponse response) {
        if (response == null || response.days() == null) {
            return response;
        }
        List<AiGuideDayResponse> days = response.days().stream()
                .filter(day -> day != null && day.items() != null)
                .map(day -> new AiGuideDayResponse(day.day(), day.title(), day.items().stream()
                        .filter(item -> item != null)
                        .filter(item -> item.placeId() != null || !isFallbackEligibleItem(item))
                        .toList()))
                .filter(day -> !day.items().isEmpty())
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
                || name.contains("미확인")
                || isLocationPrefixedGenericName(name)
                || name.contains("\uCE74\uD398\uD0D0\uBC29") || name.contains("\uB9DB\uC9D1\uD0D0\uBC29")
                || name.contains("\uC810\uC2EC\uC2DD\uC0AC") || name.contains("\uC800\uB141\uC2DD\uC0AC")
                || name.contains("\uC74C\uC2DD\uD0D0\uBC29") || name.contains("\uAE38\uAC70\uB9AC\uC74C\uC2DD")
                || name.contains("\uC1FC\uD551") || name.contains("\uD328\uC158\uAC70\uB9AC")
                || name.contains("\uAD00\uAD11") || name.contains("\uBB38\uD654\uACF5\uAC04")
                || name.contains("\uBA85\uC18C") || name.contains("\uD734\uC2DD") || name.contains("\uC0B0\uCC45")
                || name.contains("\uB3C4\uBCF4\uD0D0\uBC29") || name.contains("\uAD6C\uACBD")
                || name.contains("\uACF5\uC6D0") || name.contains("\uBC15\uBB3C\uAD00")
                || name.contains("\uBBF8\uC220\uAD00") || name.contains("\uC804\uC2DC")
                || name.contains("\uAC24\uB7EC\uB9AC") || name.contains("\uC804\uB9DD\uB300")
                || name.contains("\uD574\uC218\uC695\uC7A5") || name.contains("\uC2DC\uC7A5")
                || name.contains("숲길") || name.contains("거리") || name.contains("탐방")
                || name.contains("코스") || name.contains("부근")
                || name.equals("\uCE74\uD398") || name.equals("\uB9DB\uC9D1") || name.equals("\uC2DD\uB2F9");
    }

    /**
     * 모델이 "성수동 카페", "광복로 관광지"처럼 실제 상호가 아닌
     * 지역 + 업종/활동명만 반환한 경우를 판별한다. 이 항목은 검증된 장소로
     * 교체하지 못하면 일정에 추가할 수 있는 카드로 노출하지 않는다.
     */
    private boolean isLocationPrefixedGenericName(String normalizedName) {
        return normalizedName.matches(
                ".*(?:동|리|구|시|군|읍|면|역|로|길|거리|일대|주변)(?:카페|맛집|식당|음식점|관광지|명소|문화공간|휴식|산책|쇼핑)$"
        );
    }

    private String expectedCategoryForFallback(AiGuideItemResponse item, String question) {
        String name = String.valueOf(item.name()).toLowerCase(Locale.ROOT);
        String reason = String.valueOf(item.reason()).toLowerCase(Locale.ROOT);

        // A single question can request both food and coffee. The item text must win over the whole question.
        String category = categoryFromText(name);
        if (category != null) {
            return category;
        }
        category = categoryFromText(reason);
        return category != null ? category : categoryFromText(String.valueOf(question).toLowerCase(Locale.ROOT));
    }

    private String categoryFromText(String value) {
        if (value.contains("\uB9DB\uC9D1") || value.contains("\uC2DD\uB2F9") || value.contains("\uC810\uC2EC")
                || value.contains("\uC800\uB141") || value.contains("\uC74C\uC2DD") || value.contains("\uBE0C\uB7F0\uCE58")) {
            return "RESTAURANT";
        }
        if (value.contains("\uCE74\uD398") || value.contains("\uCEE4\uD53C")) {
            return "CAFE";
        }
        if (value.contains("\uC1FC\uD551") || value.contains("\uD328\uC158") || value.contains("\uD3B8\uC9D1\uC0F5")
                || value.contains("\uAD00\uAD11") || value.contains("\uBB38\uD654") || value.contains("\uBA85\uC18C")
                || value.contains("\uD734\uC2DD") || value.contains("\uC0B0\uCC45") || value.contains("\uB3C4\uBCF4")
                || value.contains("\uAD6C\uACBD") || value.contains("\uACF5\uC6D0")
                || value.contains("\uBC15\uBB3C\uAD00") || value.contains("\uBBF8\uC220\uAD00")
                || value.contains("\uC804\uC2DC") || value.contains("\uAC24\uB7EC\uB9AC")
                || value.contains("\uC804\uB9DD\uB300") || value.contains("\uD574\uC218\uC695\uC7A5")
                || value.contains("\uC2DC\uC7A5") || value.contains("숲길")
                || value.contains("거리") || value.contains("탐방") || value.contains("코스")) {
            return "ATTRACTION";
        }
        return null;
    }

    private boolean isGenericPlaceItem(AiGuideItemResponse item) {
        String name = normalizePlaceName(item.name());
        return name.contains("카페탐방") || name.contains("맛집탐방") || name.contains("점심식사")
                || name.contains("저녁식사") || name.contains("음식탐방") || name.contains("길거리음식")
                || name.contains("문화공간") || name.contains("문화시설") || name.contains("명소") || name.contains("휴식")
                || name.equals("카페") || name.equals("맛집") || name.equals("식당");
    }

    private String expectedCategory(AiGuideItemResponse item, String question) {
        return expectedCategoryForFallback(item, question);
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
        List<RagSearchResult> indexedResults = service == null ? List.of() : service.search(question);
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
        if ((scheduledAnchorPlaceId != null || !KakaoPlaceDiscoveryService.extractNearbyAnchor(question).isBlank())
                && !discoveredResults.isEmpty()) {
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

    /**
     * 질문이 특정 DAY 하나를 가리킬 때 모델이 다른 DAY 번호를 반환하더라도,
     * 추천 카드가 사용자가 요청한 DAY에 추가되도록 응답 범위를 고정한다.
     */
    private AiGuideResponse alignDaysWithRequestedDay(AiGuideResponse response, String question) {
        if (response == null || response.days() == null || response.days().isEmpty()) {
            return response;
        }
        int requestedDay = extractRequestedDayNumber(question);
        if (requestedDay <= 0) {
            return response;
        }

        List<AiGuideDayResponse> matchingDays = response.days().stream()
                .filter(day -> day != null && day.day() == requestedDay)
                .toList();
        if (!matchingDays.isEmpty()) {
            return new AiGuideResponse(response.answer(), matchingDays, response.externalLinks(), response.sources());
        }

        AiGuideDayResponse firstDay = response.days().stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (firstDay == null) {
            return response;
        }
        String title = firstDay.title() == null ? "" : firstDay.title()
                .replaceFirst("(?i)day\\s*\\d+", "DAY " + requestedDay);
        if (title.isBlank()) {
            title = "DAY " + requestedDay + " 추천 일정";
        }
        AiGuideDayResponse correctedDay = new AiGuideDayResponse(requestedDay, title, firstDay.items());
        return new AiGuideResponse(response.answer(), List.of(correctedDay), response.externalLinks(), response.sources());
    }

    private Long findScheduledAnchorPlaceId(String question, org.example.all_my_trip_project.domain.ai.dto.AiGuideContext context) {
        if (question == null || context == null || context.trip() == null) {
            return null;
        }
        String normalizedQuestion = normalizePlaceName(question);
        Long directlyMentionedPlaceId = context.trip().days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.placeId() != null && item.title() != null && !item.title().isBlank())
                // "식당", "카페"처럼 상호가 아닌 일반 단어는 특정 장소를 가리키지 않는다.
                // 이 경우에는 아래의 DAY별 최근 일정 기준점을 사용해야 다른 일차를 잘못 참조하지 않는다.
                .filter(item -> !isGenericAnchorTitle(normalizePlaceName(item.title())))
                .filter(item -> matchesQuestionPlace(normalizedQuestion, normalizePlaceName(item.title())))
                .map(org.example.all_my_trip_project.domain.ai.dto.AiGuideContext.Item::placeId)
                .findFirst()
                .orElse(null);
        if (directlyMentionedPlaceId != null) {
            return directlyMentionedPlaceId;
        }

        // "DAY 2 점심 후 뭐 할지"처럼 상호를 다시 적지 않은 후속 질문도
        // 선택한 일차의 마지막 일정 주변에서 실제 장소를 찾아야 한다.
        int requestedDayNumber = extractRequestedDayNumber(question);
        if (requestedDayNumber <= 0) {
            return null;
        }
        return context.trip().days().stream()
                .filter(day -> day.dayNumber() != null && day.dayNumber() == requestedDayNumber)
                .flatMap(day -> day.items().stream())
                .filter(item -> item.placeId() != null)
                .max(Comparator.comparing(
                        item -> item.startTime() == null ? LocalTime.MIN : item.startTime()
                ))
                .map(org.example.all_my_trip_project.domain.ai.dto.AiGuideContext.Item::placeId)
                .orElse(null);
    }

    private int extractRequestedDayNumber(String question) {
        if (question == null || question.isBlank()) {
            return -1;
        }
        var matcher = java.util.regex.Pattern.compile("(?i)(?:day\\s*|)([1-9][0-9]?)\\s*(?:일차|일|day)?")
                .matcher(question);
        while (matcher.find()) {
            String matched = matcher.group();
            if (matched.toLowerCase(Locale.ROOT).contains("day") || matched.contains("일")) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return -1;
    }

    private boolean matchesQuestionPlace(String normalizedQuestion, String normalizedTitle) {
        if (normalizedTitle.isBlank()) {
            return false;
        }
        String withoutBranchSuffix = normalizedTitle.replaceAll("(본점|점)$", "");
        return normalizedQuestion.contains(normalizedTitle)
                || (!withoutBranchSuffix.isBlank() && normalizedQuestion.contains(withoutBranchSuffix));
    }

    private boolean isGenericAnchorTitle(String normalizedTitle) {
        return Set.of("식당", "맛집", "음식점", "카페", "커피", "관광지", "명소", "장소", "휴식")
                .contains(normalizedTitle);
    }
}
