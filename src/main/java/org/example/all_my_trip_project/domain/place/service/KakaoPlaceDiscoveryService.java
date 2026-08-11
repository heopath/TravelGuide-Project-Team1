package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 질문과 관련된 실제 Kakao 장소를 저장하고 RAG에 바로 반영한다.
 * 검색 또는 색인 장애는 AI의 일반 추천 흐름을 막지 않는다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class KakaoPlaceDiscoveryService {

    private static final int MAX_KAKAO_SEARCHES_PER_QUESTION = 8;
    private static final int MAX_PLACES_PER_SEARCH = 3;
    private static final int MAX_DISCOVERED_PLACES = 24;
    private static final long DEFAULT_TOTAL_SEARCH_TIMEOUT_MILLIS = 7000L;

    private static final Pattern RECOMMENDATION_PHRASE = Pattern.compile(
            "(추천해?\s*줘|추천\s*해\s*줘|추천\s*해주세요|추천|알려\s*줘|알려\s*주세요|찾아\s*줘|찾아\s*주세요|해\s*줘|해주세요|좀)"
    );
    private static final Pattern TRAILING_PARTICLE = Pattern.compile("(을|를|은|는|이|가|에|에서|으로|로)$");

    private final KakaoLocalPlaceClient kakaoLocalPlaceClient;
    private final PlaceDAO placeDAO;
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider;

    @Value("${kakao.local.total-search-timeout-millis:7000}")
    private long totalSearchTimeoutMillis = DEFAULT_TOTAL_SEARCH_TIMEOUT_MILLIS;

    public List<RagSearchResult> discoverAndIndex(String question, String destination) {
        List<PlaceDTO> discovered = searchWithinBudget(question, destination).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlaceDTO::getExternalPlaceId,
                        place -> place,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream()
                .limit(MAX_DISCOVERED_PLACES)
                .toList();
        if (discovered.isEmpty()) {
            return List.of();
        }

        List<PlaceDTO> saved = new ArrayList<>();
        for (PlaceDTO place : discovered) {
            try {
                saved.add(upsertAndLoad(place));
            } catch (Exception exception) {
                log.warn("Failed to save Kakao place. externalPlaceId={}, name={}",
                        place.getExternalPlaceId(), place.getName(), exception);
            }
        }
        if (saved.isEmpty()) {
            return List.of();
        }

        PlaceRagService placeRagService = placeRagServiceProvider.getIfAvailable();
        if (placeRagService == null) {
            return List.of();
        }
        try {
            placeRagService.indexPlaces(saved);
        } catch (Exception exception) {
            log.warn("Kakao places were saved but RAG indexing failed. count={}", saved.size(), exception);
        }
        return saved.stream().map(placeRagService::toSearchResult).toList();
    }

    private List<PlaceDTO> searchWithinBudget(String question, String destination) {
        long timeoutMillis = Math.max(1L, totalSearchTimeoutMillis);
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        List<PlaceDTO> discovered = new ArrayList<>();
        for (String keyword : searchKeywords(question, destination)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                log.warn("Kakao Local search budget exhausted. timeoutMillis={}", timeoutMillis);
                break;
            }
            // Keep a few candidates from every location/category query. Without this,
            // the first location's results can crowd later days out of the prompt.
            List<PlaceDTO> searchedPlaces = kakaoLocalPlaceClient.search(keyword, Duration.ofNanos(remainingNanos));
            if (searchedPlaces != null) {
                discovered.addAll(searchedPlaces.stream()
                        .limit(MAX_PLACES_PER_SEARCH)
                        .toList());
            }
        }
        return discovered;
    }

    private PlaceDTO upsertAndLoad(PlaceDTO place) {
        Long placeId = placeDAO.upsert(place);
        return placeDAO.findById(placeId).orElseThrow(
                () -> new IllegalStateException("Saved Kakao place cannot be loaded. placeId=" + placeId)
        );
    }

    static String searchKeyword(String question, String destination) {
        String cleanedQuestion = normalizeQuestion(question);
        String cleanedDestination = destination == null ? "" : destination.trim();
        if (cleanedDestination.isBlank()) {
            return cleanedQuestion;
        }
        if (cleanedQuestion.contains(cleanedDestination)) {
            return cleanedQuestion;
        }
        return (cleanedDestination + " " + cleanedQuestion).trim();
    }

    static List<String> searchKeywords(String question, String destination) {
        String primary = searchKeyword(question, destination);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (!primary.isBlank() && primary.length() <= 40) {
            keywords.add(primary);
        }

        boolean hasDaySpecificKeywords = addDaySpecificKeywords(keywords, question);

        List<String> locations = extractLocations(question);
        if (locations.isEmpty() && destination != null && !destination.isBlank()) {
            locations = List.of(destination.trim());
        }
        for (String location : locations) {
            for (String term : extractVenueSearchTerms(question)) {
                keywords.add(location + " " + term);
            }
        }
        if (!hasDaySpecificKeywords) {
            List<String> categories = extractCategories(question);
            for (String location : locations) {
                for (String category : categories) {
                    keywords.add(location + " " + category);
                }
            }
        }
        if (keywords.isEmpty() && !primary.isBlank()) {
            keywords.add(primary);
        }
        return keywords.stream().limit(MAX_KAKAO_SEARCHES_PER_QUESTION).toList();
    }

    private static boolean addDaySpecificKeywords(LinkedHashSet<String> keywords, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        List<String> commonCategories = extractCommonCategories(question);
        Pattern daySegment = Pattern.compile(
                "(?s)(?:첫\\s*날|둘째|셋째|넷째|다섯째|[1-9]\\s*일차)\\s*(?:날)?\\s*(?:은|는)?\\s*([가-힣0-9]{2,12})(.*?)(?=(?:,?\\s*(?:첫\\s*날|둘째|셋째|넷째|다섯째|[1-9]\\s*일차))|$)"
        );
        var matcher = daySegment.matcher(question);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String location = trimLocationSuffix(matcher.group(1));
            String segment = matcher.group(2);
            LinkedHashSet<String> categories = new LinkedHashSet<>(commonCategories);
            categories.addAll(extractSpecificCategories(segment));
            if (categories.isEmpty()) {
                categories.add("장소");
            }
            for (String category : categories) {
                keywords.add(location + " " + category);
            }
        }
        return found;
    }

    private static List<String> extractLocations(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();

        // Preserve the day order first so DAY 1 through DAY N get fair search coverage.
        collectMatches(locations, Pattern.compile(
                "(?:첫\\s*날|둘째|셋째|넷째|다섯째|[1-9]\\s*일차)\\s*(?:날)?\\s*(?:은|는)?\\s*([가-힣0-9]{2,12})"
        ), question);
        // Administrative-area and station names: "성수동", "부산진구", "강남역".
        collectMatches(locations, Pattern.compile(
                "([가-힣0-9]+(?:특별시|광역시|특별자치시|특별자치도|도|시|군|구|동|읍|면|리|역))"
        ), question);
        // Natural Korean expressions: "성수에서", "여수 근처", "강릉의 카페".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})(?=\\s*(?:에서|에(?:서)?|근처|주변|쪽|의))"
        ), question);
        // Linked locations: "성수와 연남에서", "여수, 순천 카페".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})(?=\\s*(?:와|과|,|·))"
        ), question);
        // Compact searches without a particle: "대구 카페", "전주 맛집".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})\\s*(?=(?:카페|커피|맛집|식당|밥집|술집|바|펍|클럽|놀거리|관광지|쇼핑))"
        ), question);
        return locations.stream()
                .filter(location -> !isNonLocationWord(location))
                .limit(4)
                .toList();
    }

    private static void collectMatches(LinkedHashSet<String> targets, Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
    }

    private static boolean isNonLocationWord(String value) {
        return value.equals("첫째") || value.equals("둘째") || value.equals("셋째")
                || value.equals("넷째") || value.equals("다섯째") || value.equals("여행")
                || value.equals("일정") || value.equals("추천");
    }

    private static String trimLocationSuffix(String location) {
        return location.replaceFirst("(에서|에게서|은|는)$", "");
    }

    private static List<String> extractCategories(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<String> categories = new LinkedHashSet<>(extractCommonCategories(value));
        categories.addAll(extractSpecificCategories(value));
        return categories.isEmpty() ? List.of("장소") : List.copyOf(categories);
    }

    private static List<String> extractCommonCategories(String value) {
        List<String> categories = new ArrayList<>();
        if (value.contains("카페") || value.contains("커피")) {
            categories.add("카페");
        }
        if (value.contains("맛집") || value.contains("밥집") || value.contains("식당")
                || value.contains("점심") || value.contains("저녁") || value.contains("음식")) {
            categories.add("맛집");
        }
        return categories;
    }

    private static List<String> extractSpecificCategories(String value) {
        List<String> categories = new ArrayList<>();
        if (value.contains("술집") || value.contains("바") || value.contains("펍")
                || value.contains("유흥") || value.contains("밤")) {
            categories.add("술집");
        }
        if (value.contains("클럽")) {
            categories.add("클럽");
        }
        if (value.contains("놀거리") || value.contains("데이트") || value.contains("즐길")) {
            categories.add("놀거리");
        }
        if (value.contains("관광") || value.contains("명소") || value.contains("구경")) {
            categories.add("관광지");
        }
        if (value.contains("쇼핑") || value.contains("구매")) {
            categories.add("쇼핑");
        }
        return categories;
    }

    /**
     * 카카오에 그대로 전달할 핵심 업종어다. "혼술 LP 바"처럼 긴 문장 검색이
     * 실패해도 "부산 LP바" 후보를 별도로 확보해 실제 상호명 추천을 돕는다.
     */
    private static List<String> extractVenueSearchTerms(String question) {
        String value = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (value.contains("lp") && value.contains("바")) terms.add("LP바");
        if (value.contains("와인")) terms.add("와인바");
        if (value.contains("칵테일")) terms.add("칵테일바");
        if (value.contains("버거")) terms.add("버거");
        if (value.contains("피자")) terms.add("피자");
        if (value.contains("치킨")) terms.add("치킨");
        if (value.contains("브런치")) terms.add("브런치");
        if (value.contains("디저트")) terms.add("디저트");
        if (value.contains("이탈리안")) terms.add("이탈리안");
        if (value.contains("중식")) terms.add("중식");
        if (value.contains("일식")) terms.add("일식");
        if (value.contains("한식")) terms.add("한식");
        return List.copyOf(terms);
    }

    private static String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String normalized = question.replaceAll("[?!.,]", " ");
        normalized = RECOMMENDATION_PHRASE.matcher(normalized).replaceAll(" ");
        // "편집샵 세개만 추천해줘"처럼 수량 표현까지 카카오에 전달하면
        // 실제 장소 키워드 검색이 비어 버릴 수 있으므로 검색 의도만 남긴다.
        normalized = normalized.replaceAll("(?:^|\\s)(?:[0-9]+|한|두|세|네|다섯|몇)\\s*개(?:만|정도)?(?=\\s|$)", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        String[] tokens = normalized.split(" ");
        if (tokens.length == 0) {
            return normalized;
        }
        tokens[tokens.length - 1] = TRAILING_PARTICLE.matcher(tokens[tokens.length - 1]).replaceFirst("");
        return String.join(" ", tokens).trim();
    }
}
