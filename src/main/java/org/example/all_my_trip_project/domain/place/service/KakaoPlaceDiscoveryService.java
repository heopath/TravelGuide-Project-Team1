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
import java.util.Optional;
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
    // "다른 식당/카페" 요청은 직전 추천을 제외하고도 새 후보가 남아야 한다.
    // 카카오의 상위 3건만 가져오면 최근 추천만 남아 빈 결과가 되기 쉽다.
    private static final int MAX_ALTERNATIVE_PLACES_PER_SEARCH = 8;
    private static final int MAX_DISCOVERED_PLACES = 24;
    private static final long DEFAULT_TOTAL_SEARCH_TIMEOUT_MILLIS = 7000L;
    private static final int WALKING_RADIUS_METERS = 2_000;
    private static final int DEFAULT_NEARBY_RADIUS_METERS = 2_000;
    private static final int VEHICLE_RADIUS_METERS = 5_000;

    private static final Pattern RECOMMENDATION_PHRASE = Pattern.compile(
            "(추천해?\s*줘|추천\s*해\s*줘|추천\s*해주세요|추천|알려\s*줘|알려\s*주세요|찾아\s*줘|찾아\s*주세요|해\s*줘|해주세요|좀)"
    );
    private static final Pattern TRAILING_PARTICLE = Pattern.compile("(을|를|은|는|이|가|에|에서|으로|로)$");
    private static final Pattern NEARBY_ANCHOR = Pattern.compile("^(.+?)(?:\\s*(?:근처|주변))");
    private static final Pattern DAY_PREFIX = Pattern.compile(
            "^(?:(?i:day)\\s*[1-9]|[1-9]\\s*일\\s*차|첫\\s*날|둘째\\s*날|셋째\\s*날|넷째\\s*날)(?:에|은|는)?\\s*"
    );
    private static final String DAY_EXPRESSION =
            "(?:(?i:day)\\s*[1-9]|[1-9]\\s*일\\s*차|첫\\s*날|둘째(?:\\s*날)?|셋째(?:\\s*날)?|넷째(?:\\s*날)?|다섯째(?:\\s*날)?)";
    private static final Pattern DAY_MARKER = Pattern.compile(DAY_EXPRESSION);
    private static final Pattern DAY_LOCATION = Pattern.compile(
            DAY_EXPRESSION + "\\s*(?:에|은|는)?\\s*([가-힣0-9]{2,12}?)(?:을|를|은|는|이|가|에|에서|으로|로)?(?=\\s|,|$)"
    );
    private static final Pattern VISIT_ACTION = Pattern.compile(
            "^(.+?)(?:을|를|으로|로)\\s*(?:먹고|먹은|방문(?:하고|한)?|들르고|갔다가|다녀와서|간 뒤|간후|간 후)"
    );
    private static final Pattern REGION_ANCHOR = Pattern.compile(
            "(?:^|\\s)([가-힣0-9]{2,20}?)(?:에서|에)(?=\\s|$)"
    );
    private static final List<String> BAKERY_TERMS = List.of(
            "제과", "제빵", "제과점", "베이커리", "빵집", "도넛", "케이크", "디저트", "페이스트리", "마카롱"
    );
    private static final List<String> BAR_TERMS = List.of(
            "술집", "와인바", "칵테일바", "lp바", "펍", "호프", "포차", "이자카야", "주점", "바틀샵"
    );
    private static final List<String> MEAL_TERMS = List.of(
            "식당", "음식점", "맛집", "밥집", "한식", "중식", "일식", "양식", "분식", "restaurant", "dining"
    );
    private static final List<String> ATTRACTION_TERMS = List.of(
            "관광", "명소", "구경", "산책", "공원", "박물관", "미술관", "전시", "갤러리", "궁",
            "사찰", "해수욕장", "바다", "해변", "전망대", "야경", "포토스팟", "랜드마크",
            "문화", "역사관", "기념관", "테마파크", "놀이공원", "아쿠아리움", "동물원",
            "수목원", "식물원", "오름", "폭포", "호수", "둘레길", "산책로", "트레킹",
            "등산", "케이블카", "스카이워크", "체험", "공방", "원데이클래스", "액티비티",
            "레저", "서핑", "요트", "크루즈", "자전거", "방탈출", "공연", "극장",
            "놀거리", "볼거리", "데이트", "즐길", "놀 수", "할 수 있는", "뭐 할", "갈 곳", "가볼"
    );
    private static final List<String> SHOPPING_TERMS = List.of(
            "쇼핑", "편집샵", "소품샵", "빈티지", "의류", "잡화", "기념품", "서점",
            "레코드", "스니커즈", "백화점", "아울렛", "시장", "몰", "상점", "패션"
    );

    private enum VenueType {
        MEAL, CAFE, BAKERY, BAR, ATTRACTION, SHOPPING, UNKNOWN
    }

    private final KakaoLocalPlaceClient kakaoLocalPlaceClient;
    private final PlaceDAO placeDAO;
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider;

    @Value("${kakao.local.total-search-timeout-millis:7000}")
    private long totalSearchTimeoutMillis = DEFAULT_TOTAL_SEARCH_TIMEOUT_MILLIS;

    public List<RagSearchResult> discoverAndIndex(String question, String destination) {
        return discoverAndIndex(question, destination, null);
    }

    public List<RagSearchResult> discoverAndIndex(String question, String destination, Long scheduledPlaceId) {
        return discoverAndIndex(question, destination, scheduledPlaceId, 1);
    }

    /**
     * "다른 식당/카페" 요청은 첫 페이지의 후보를 대화 이력에서 제외한 뒤에도
     * 새 상호가 남아야 한다. 페이지 2만 조회하면 검색어에 따라 결과가 비어
     * 후보 없음으로 끝날 수 있으므로, 넓어진 첫 페이지와 다음 페이지를 함께
     * 확보한 뒤 AI 서비스가 직전 추천 장소를 제외한다.
     */
    public List<RagSearchResult> discoverAlternativeAndIndex(String question, String destination,
                                                               Long scheduledPlaceId) {
        return java.util.stream.Stream.concat(
                        discoverAndIndex(question, destination, scheduledPlaceId, 1).stream(),
                        discoverAndIndex(question, destination, scheduledPlaceId, 2).stream()
                )
                .collect(java.util.stream.Collectors.toMap(
                        RagSearchResult::source,
                        result -> result,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream()
                .limit(MAX_DISCOVERED_PLACES)
                .toList();
    }

    private List<RagSearchResult> discoverAndIndex(String question, String destination, Long scheduledPlaceId,
                                                     int page) {
        List<PlaceDTO> discovered = searchWithinBudget(question, destination, scheduledPlaceId, page).stream()
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
        if (placeRagService != null) {
            try {
                placeRagService.indexPlaces(saved);
            } catch (Exception exception) {
                log.warn("Kakao places were saved but RAG indexing failed. count={}", saved.size(), exception);
            }
        }
        // RAG is optional. Fresh Kakao results must still be passed to the AI so that
        // an actual venue or attraction can be recommended and added to an itinerary.
        return saved.stream().map(this::toSearchResult).toList();
    }

    private RagSearchResult toSearchResult(PlaceDTO place) {
        String detailedCategory = nullToEmpty(place.getDescription());
        return new RagSearchResult(
                "place:" + place.getPlaceId(),
                "장소명: " + nullToEmpty(place.getName())
                        + "\n지역: " + nullToEmpty(place.getRegion())
                        + "\n카테고리: " + nullToEmpty(place.getCategory())
                        + (detailedCategory.isBlank() ? "" : "\n세부업종: " + detailedCategory)
                        + "\n주소: " + nullToEmpty(place.getAddress()),
                place.getPlaceId(),
                nullToEmpty(place.getName()),
                nullToEmpty(place.getCategory()),
                nullToEmpty(place.getAddress()),
                nullToEmpty(place.getWebsiteUrl())
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<PlaceDTO> searchWithinBudget(String question, String destination, Long scheduledPlaceId, int page) {
        long timeoutMillis = Math.max(1L, totalSearchTimeoutMillis);
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        List<PlaceDTO> discovered = new ArrayList<>();
        boolean nearbyRequest = scheduledPlaceId != null || !resolveSearchAnchor(question, destination).isBlank();
        int searchCount = addNearbyCategoryCandidates(discovered, question, destination, scheduledPlaceId, deadline, page);
        // 일정에 들어 있는 실제 기준 장소는 좌표 반경 검색 결과만 사용한다.
        // 다만 "애월 근처", "서귀포 근처"처럼 지역명만 기준으로 한 요청은 좌표를 얻지
        // 못할 수 있으므로, 후보가 비었을 때 지역 + 업종 키워드 검색으로 이어간다.
        if (nearbyRequest && (scheduledPlaceId != null || !discovered.isEmpty())) {
            return discovered;
        }
        for (String keyword : searchKeywords(question, destination)) {
            if (searchCount >= MAX_KAKAO_SEARCHES_PER_QUESTION) {
                break;
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                log.warn("Kakao Local search budget exhausted. timeoutMillis={}", timeoutMillis);
                break;
            }
            // Keep a few candidates from every location/category query. Without this,
            // the first location's results can crowd later days out of the prompt.
            List<PlaceDTO> searchedPlaces = page == 1
                    ? kakaoLocalPlaceClient.search(keyword, Duration.ofNanos(remainingNanos))
                    : kakaoLocalPlaceClient.search(keyword, Duration.ofNanos(remainingNanos), page);
            if (searchedPlaces != null) {
                discovered.addAll(searchedPlaces.stream()
                        // 여행 목적지와 다른 지역의 동명 장소가 실제 일정 카드로
                        // 연결되지 않도록, 일반 검색 결과도 목적지 주소를 확인한다.
                        .filter(place -> matchesDestination(place, destination))
                        .filter(place -> matchesRequestedVenueType(place, question))
                        .limit(placesPerSearch(question))
                        .toList());
            }
            searchCount++;
        }
        return discovered;
    }

    private int addNearbyCategoryCandidates(List<PlaceDTO> discovered, String question, String destination,
                                            Long scheduledPlaceId, long deadline, int page) {
        String anchor = resolveSearchAnchor(question, destination);
        List<String> categoryCodes = extractNearbyCategoryCodes(question);
        List<String> nearbyKeywordTerms = extractNearbyKeywordTerms(question);
        if ((categoryCodes.isEmpty() && nearbyKeywordTerms.isEmpty())
                || (anchor.isBlank() && scheduledPlaceId == null)) {
            return 0;
        }

        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return 0;
        }
        Optional<PlaceDTO> anchorPlace = findAnchorPlace(anchor, destination, scheduledPlaceId,
                Duration.ofNanos(remainingNanos));
        if (anchorPlace.isEmpty()) {
            return 1;
        }

        int searchCount = 1;
        for (String categoryCode : categoryCodes) {
            if (searchCount >= MAX_KAKAO_SEARCHES_PER_QUESTION) {
                break;
            }
            remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            PlaceDTO anchorPoint = anchorPlace.get();
            int requestedRadius = nearbyRadiusMeters(question);
            List<PlaceDTO> nearbyPlaces = searchNearbyCandidates(
                    categoryCode, anchorPoint, requestedRadius, Duration.ofNanos(remainingNanos), page);
            if (nearbyPlaces.isEmpty() && allowsVehicleRadiusFallback(question)) {
                remainingNanos = deadline - System.nanoTime();
                if (remainingNanos > 0) {
                    nearbyPlaces = searchNearbyCandidates(
                            categoryCode, anchorPoint, VEHICLE_RADIUS_METERS, Duration.ofNanos(remainingNanos), page);
                }
            }
            if (!nearbyPlaces.isEmpty()) {
                discovered.addAll(nearbyPlaces.stream()
                        .filter(place -> matchesRequestedVenueType(place, question))
                        .limit(placesPerSearch(question))
                        .toList());
            }
            searchCount++;
        }
        for (String keyword : nearbyKeywordTerms) {
            if (searchCount >= MAX_KAKAO_SEARCHES_PER_QUESTION) {
                break;
            }
            remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            PlaceDTO anchorPoint = anchorPlace.get();
            int requestedRadius = nearbyRadiusMeters(question);
            List<PlaceDTO> nearbyPlaces = searchNearbyKeywordCandidates(
                    keyword, anchorPoint, requestedRadius, Duration.ofNanos(remainingNanos), page);
            if (nearbyPlaces.isEmpty() && allowsVehicleRadiusFallback(question)) {
                remainingNanos = deadline - System.nanoTime();
                if (remainingNanos > 0) {
                    nearbyPlaces = searchNearbyKeywordCandidates(
                            keyword, anchorPoint, VEHICLE_RADIUS_METERS, Duration.ofNanos(remainingNanos), page);
                }
            }
            if (!nearbyPlaces.isEmpty()) {
                discovered.addAll(nearbyPlaces.stream()
                        .filter(place -> matchesRequestedVenueType(place, question))
                        .limit(placesPerSearch(question))
                        .toList());
            }
            searchCount++;
        }
        return searchCount;
    }

    private List<PlaceDTO> searchNearbyCandidates(String categoryCode, PlaceDTO anchor, int radiusMeters,
                                                   Duration timeout, int page) {
        List<PlaceDTO> results = page == 1
                ? (radiusMeters == DEFAULT_NEARBY_RADIUS_METERS
                ? kakaoLocalPlaceClient.searchByCategory(
                        categoryCode, anchor.getLongitude(), anchor.getLatitude(), timeout)
                : kakaoLocalPlaceClient.searchByCategory(
                        categoryCode, anchor.getLongitude(), anchor.getLatitude(), radiusMeters, timeout))
                : kakaoLocalPlaceClient.searchByCategory(
                        categoryCode, anchor.getLongitude(), anchor.getLatitude(), radiusMeters, timeout, page);
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(this::hasCoordinates)
                .map(place -> new NearbyPlace(place, distanceMeters(anchor, place)))
                .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                .sorted(java.util.Comparator.comparingDouble(NearbyPlace::distanceMeters))
                .map(NearbyPlace::place)
                .toList();
    }

    private List<PlaceDTO> searchNearbyKeywordCandidates(String keyword, PlaceDTO anchor, int radiusMeters,
                                                          Duration timeout, int page) {
        List<PlaceDTO> results = page == 1
                ? kakaoLocalPlaceClient.searchNearby(
                keyword, anchor.getLongitude(), anchor.getLatitude(), radiusMeters, timeout)
                : kakaoLocalPlaceClient.searchNearby(
                keyword, anchor.getLongitude(), anchor.getLatitude(), radiusMeters, timeout, page);
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(this::hasCoordinates)
                .map(place -> new NearbyPlace(place, distanceMeters(anchor, place)))
                .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                .sorted(java.util.Comparator.comparingDouble(NearbyPlace::distanceMeters))
                .map(NearbyPlace::place)
                .toList();
    }

    private int nearbyRadiusMeters(String question) {
        String value = question == null ? "" : question;
        if (value.contains("\uB3C4\uBCF4") || value.contains("\uAC78\uC5B4") || value.contains("\uB3C4\uBCF4 10\uBD84")) {
            return WALKING_RADIUS_METERS;
        }
        if (value.contains("\uC790\uB3D9\uCC28") || value.contains("\uD0DD\uC2DC") || value.contains("\uC6B4\uC804")) {
            return VEHICLE_RADIUS_METERS;
        }
        return DEFAULT_NEARBY_RADIUS_METERS;
    }

    private boolean allowsVehicleRadiusFallback(String question) {
        String value = question == null ? "" : question;
        return !(value.contains("\uB3C4\uBCF4") || value.contains("\uAC78\uC5B4") || value.contains("\uB3C4\uBCF4 10\uBD84"));
    }

    private double distanceMeters(PlaceDTO from, PlaceDTO to) {
        double earthRadiusMeters = 6_371_000d;
        double latitudeDifference = Math.toRadians(to.getLatitude().doubleValue() - from.getLatitude().doubleValue());
        double longitudeDifference = Math.toRadians(to.getLongitude().doubleValue() - from.getLongitude().doubleValue());
        double startLatitude = Math.toRadians(from.getLatitude().doubleValue());
        double endLatitude = Math.toRadians(to.getLatitude().doubleValue());
        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude)
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        return 2 * earthRadiusMeters * Math.asin(Math.sqrt(haversine));
    }

    private record NearbyPlace(PlaceDTO place, double distanceMeters) {
    }

    private Optional<PlaceDTO> findAnchorPlace(String anchor, String destination, Long scheduledPlaceId, Duration timeout) {
        if (scheduledPlaceId != null) {
            Optional<PlaceDTO> scheduledPlace = placeDAO.findById(scheduledPlaceId)
                    .filter(this::hasCoordinates);
            if (scheduledPlace.isPresent()) {
                return scheduledPlace;
            }
        }
        String trimmedDestination = destination == null ? "" : destination.trim();
        List<PlaceDTO> candidates = trimmedDestination.isBlank()
                ? kakaoLocalPlaceClient.search(anchor, timeout)
                : kakaoLocalPlaceClient.search(trimmedDestination + " " + anchor, timeout);

        Optional<PlaceDTO> destinationMatch = candidates.stream()
                .filter(place -> hasCoordinates(place) && matchesDestination(place, trimmedDestination))
                .findFirst();
        if (destinationMatch.isPresent() || trimmedDestination.isBlank()) {
            return destinationMatch;
        }

        return kakaoLocalPlaceClient.search(anchor, timeout).stream()
                .filter(place -> hasCoordinates(place) && matchesDestination(place, trimmedDestination))
                .findFirst();
    }

    /**
     * "광안리에서 놀거리"처럼 지역이 곧 검색 반경인 요청도 좌표 기준 카테고리
     * 검색으로 처리한다. 여행지 전체(예: 부산광역시)만 적힌 경우는 너무 넓으므로
     * 기존 키워드 검색 흐름을 유지한다.
     */
    private String resolveSearchAnchor(String question, String destination) {
        String nearbyAnchor = extractNearbyAnchor(question);
        if (!nearbyAnchor.isBlank()) {
            return nearbyAnchor;
        }

        // "광안리에서 놀거리"처럼 조사가 지역명에 바로 붙은 경우는 일반 위치
        // 추출 정규식이 "광안리에서" 전체를 잡을 수 있다. 이 경우에도 카카오
        // 좌표 반경 검색으로 이어질 수 있도록 지역명만 먼저 분리한다.
        var regionAnchorMatcher = REGION_ANCHOR.matcher(question == null ? "" : question);
        while (regionAnchorMatcher.find()) {
            String regionAnchor = regionAnchorMatcher.group(1).trim();
            if (!isNonLocationWord(regionAnchor) && !isTravelDestination(regionAnchor, destination)) {
                return regionAnchor;
            }
        }

        List<String> locations = extractLocations(question);
        if (locations.size() != 1) {
            return "";
        }

        String location = locations.getFirst();
        return isTravelDestination(location, destination) ? "" : location;
    }

    private boolean isTravelDestination(String location, String destination) {
        String destinationName = destination == null ? "" : destination.trim();
        String normalizedDestination = destinationName
                .replaceAll("(특별시|광역시|특별자치시|특별자치도)$", "")
                .replaceAll("\\s+", "");
        return location.equals(destinationName) || location.equals(normalizedDestination);
    }

    private boolean hasCoordinates(PlaceDTO place) {
        return place.getLongitude() != null && place.getLatitude() != null;
    }

    private boolean matchesDestination(PlaceDTO place, String destination) {
        if (destination.isBlank()) {
            return true;
        }
        String normalizedDestination = destination.replaceAll("(특별시|광역시|특별자치시|특별자치도)$", "");
        String address = (String.valueOf(place.getAddress()) + " " + String.valueOf(place.getRegion())
                + " " + String.valueOf(place.getCity())).replaceAll("\\s+", "");
        return address.contains(destination) || address.contains(normalizedDestination);
    }

    static int placesPerSearch(String question) {
        if (question == null || question.isBlank()) {
            return MAX_PLACES_PER_SEARCH;
        }
        String normalized = normalizeQuestion(question);
        return question.contains("다른") || question.contains("말고") || question.contains("재추천")
                || question.contains("다시 추천") || normalized.contains("다른곳")
                || normalized.contains("다른데") || normalized.contains("또추천")
                ? MAX_ALTERNATIVE_PLACES_PER_SEARCH
                : MAX_PLACES_PER_SEARCH;
    }

    /**
     * Kakao의 상위 음식점(FD6) 그룹에는 제과점·베이커리·주점도 섞일 수 있다.
     * 상호와 category_name에서 보존한 세부업종을 함께 사용해 요청 업종과 맞는
     * 실제 장소만 AI 후보로 전달한다.
     */
    private boolean matchesRequestedVenueType(PlaceDTO place, String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<VenueType> requestedTypes = requestedVenueTypes(value);
        if (requestedTypes.isEmpty()) {
            return true;
        }
        VenueType actualType = classifyVenueType(place);
        // 업종을 명시한 요청에서는 유형을 판별하지 못한 후보도 실제 일정 카드에
        // 연결하지 않는다. 다른 업종의 장소가 식당·카페·쇼핑 카드로 보이는 것을 막는다.
        return requestedTypes.contains(actualType);
    }

    private static LinkedHashSet<VenueType> requestedVenueTypes(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<VenueType> types = new LinkedHashSet<>();
        boolean hasSpecificNonMealIntent = containsCafeIntent(value) || containsBakeryIntent(value)
                || containsBarIntent(value) || containsAttractionIntent(value) || containsShoppingIntent(value);

        // "점심을 먹고 뭐 할까", "일정 중간에 갈 곳"은 식당을 다시 찾는 뜻이 아니라
        // 활동 장소를 찾는 뜻이다. 단, 카페·박물관처럼 업종을 명시한 경우에는 그 업종을 따른다.
        if ((isFollowUpActivityRequest(value) || isScheduleGapActivityRequest(value)) && !hasSpecificNonMealIntent) {
            types.add(VenueType.ATTRACTION);
            return types;
        }

        if (containsRestaurantIntent(value)) {
            types.add(VenueType.MEAL);
        }
        if (containsBakeryIntent(value)) {
            types.add(VenueType.BAKERY);
        } else if (containsCafeIntent(value)) {
            types.add(VenueType.CAFE);
        }
        if (containsBarIntent(value)) {
            types.add(VenueType.BAR);
        }
        if (containsAttractionIntent(value)) {
            types.add(VenueType.ATTRACTION);
        }
        if (containsShoppingIntent(value)) {
            types.add(VenueType.SHOPPING);
        }
        return types;
    }

    private static VenueType classifyVenueType(PlaceDTO place) {
        String name = place.getName() == null ? "" : place.getName();
        String category = place.getCategory() == null ? "" : place.getCategory();
        String description = place.getDescription() == null ? "" : place.getDescription();
        String searchable = (name + " " + category + " " + description).toLowerCase(java.util.Locale.ROOT);
        if (containsAny(searchable, BAKERY_TERMS)) {
            return VenueType.BAKERY;
        }
        if (containsAny(searchable, BAR_TERMS)) {
            return VenueType.BAR;
        }
        if (searchable.contains("카페") || searchable.contains("커피") || searchable.contains("로스터리")
                || searchable.contains("티하우스") || searchable.contains("cafe") || searchable.contains("coffee")) {
            return VenueType.CAFE;
        }
        // Kakao의 쇼핑·문화 장소는 category_group_code가 없어서 DB에는 ATTRACTION으로
        // 보관될 수 있다. 이때도 세부업종/상호의 쇼핑 표현을 먼저 확인해야 한다.
        if (containsAny(searchable, SHOPPING_TERMS)) {
            return VenueType.SHOPPING;
        }
        if ("CAFE".equalsIgnoreCase(category)) {
            return VenueType.CAFE;
        }
        if ("RESTAURANT".equalsIgnoreCase(category)) {
            return VenueType.MEAL;
        }
        if (containsAny(searchable, MEAL_TERMS)) {
            return VenueType.MEAL;
        }
        if ("ATTRACTION".equalsIgnoreCase(category)) {
            return VenueType.ATTRACTION;
        }
        if (containsAny(searchable, ATTRACTION_TERMS)) {
            return VenueType.ATTRACTION;
        }
        return VenueType.UNKNOWN;
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    public static String extractLocationHint(String question) {
        return extractLocations(question).stream().findFirst().orElse("");
    }

    public static String extractNearbyAnchor(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        int nearbyIndex = question.indexOf("\uadfc\ucc98");
        if (nearbyIndex > 0) {
            String anchor = cleanNearbyAnchor(question.substring(0, nearbyIndex));
            if (!anchor.isBlank() && anchor.length() <= 40) {
                return anchor;
            }
        }
        var matcher = NEARBY_ANCHOR.matcher(question.trim());
        if (!matcher.find()) {
            return "";
        }
        String anchor = matcher.group(1).trim();
        anchor = DAY_PREFIX.matcher(anchor).replaceFirst("").trim();
        var actionMatcher = VISIT_ACTION.matcher(anchor);
        if (actionMatcher.find()) {
            anchor = actionMatcher.group(1).trim();
        }
        anchor = anchor.replaceFirst("(?:에서|에|의)$", "").trim();
        anchor = cleanNearbyAnchor(anchor);
        return anchor.length() <= 40 ? anchor : "";
    }

    private static String cleanNearbyAnchor(String value) {
        String anchor = value == null ? "" : value.trim();
        anchor = anchor.replaceFirst("^\\s*[0-9]+\uC77C\uCC28\uC5D0\\s*", "");
        anchor = DAY_PREFIX.matcher(anchor).replaceFirst("").trim();
        // "동빙고로 갔다가 그 근처"처럼 기준 장소 뒤에 붙는 연결어를 먼저 걷어낸다.
        anchor = anchor.replaceFirst("\\s*(?:그|거기|그곳)\\s*$", "").trim();
        anchor = anchor.replaceFirst(
                "(?:을|를|으로|로)\\s*(?:먹고|먹은\\s*후|방문하고|방문한\\s*후|들르고|갔다가|다녀와서)\\s*$",
                ""
        ).trim();
        var actionMatcher = VISIT_ACTION.matcher(anchor);
        if (actionMatcher.find()) {
            anchor = actionMatcher.group(1).trim();
        }
        return anchor.replaceFirst("(?:\uC5D0\uC11C|\uC5D0\uAC8C\uC11C|으로|로|\uC740|\uB294|\uC744|\uB97C)$", "").trim();
    }

    private static List<String> extractNearbyCategoryCodes(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<String> categoryCodes = new LinkedHashSet<>();
        boolean asksForActivityAfterMeal = isFollowUpActivityRequest(value)
                && (value.contains("점심") || value.contains("식사") || value.contains("밥") || value.contains("먹고"));
        boolean asksForActivityAfterVisit = isFollowUpActivityRequest(value)
                && (!extractNearbyAnchor(value).isBlank() || value.contains("갔다가") || value.contains("방문 후")
                || value.contains("방문하고") || value.contains("들르고") || value.contains("다녀와서"));
        boolean asksForScheduleGapActivity = isScheduleGapActivityRequest(value);
        boolean asksForNearbyActivity = asksForActivityAfterMeal || asksForActivityAfterVisit
                || asksForScheduleGapActivity;
        if (asksForNearbyActivity) {
            categoryCodes.add("AT4");
        }
        if (value.contains("\uCE74\uD398") || value.contains("\uCEE4\uD53C")) {
            categoryCodes.add("CE7");
        }
        if (value.contains("\uB9DB\uC9D1") || value.contains("\uC2DD\uB2F9") || value.contains("\uC810\uC2EC")
                || value.contains("\uC800\uB141") || value.contains("\uBC25")) {
            categoryCodes.add("FD6");
        }
        if (value.contains("\uAD00\uAD11") || value.contains("\uAD6C\uACBD") || value.contains("\uC0B0\uCC45")) {
            categoryCodes.add("AT4");
        }
        if (value.contains("카페") || value.contains("커피")) {
            categoryCodes.add("CE7");
        }
        if (containsCafeIntent(value)) {
            categoryCodes.add("CE7");
        }
        if (containsBakeryIntent(value) || containsBarIntent(value)) {
            categoryCodes.add("FD6");
        }
        if (containsRestaurantIntent(value)) {
            categoryCodes.add("FD6");
        }
        if (containsAttractionIntent(value)) {
            categoryCodes.add("AT4");
        }
        if (!asksForNearbyActivity && (value.contains("맛집") || value.contains("밥집") || value.contains("식당")
                || value.contains("점심") || value.contains("저녁") || value.contains("음식"))) {
            categoryCodes.add("FD6");
        }
        // "점심 먹고 뭐 할지"는 식당을 다시 찾는 요청이 아니라, 현재 일정 장소
        // 주변에서 이어서 방문할 관광·문화 장소를 찾는 요청이다.
        if (asksForNearbyActivity) {
            categoryCodes.remove("FD6");
        }
        return List.copyOf(categoryCodes);
    }

    private static List<String> extractNearbyKeywordTerms(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        boolean asksForActivityAfterMeal = isFollowUpActivityRequest(value)
                && (value.contains("점심") || value.contains("식사") || value.contains("밥") || value.contains("먹고"));
        boolean asksForActivityAfterVisit = isFollowUpActivityRequest(value)
                && (!extractNearbyAnchor(value).isBlank() || value.contains("갔다가") || value.contains("방문 후")
                || value.contains("방문하고") || value.contains("들르고") || value.contains("다녀와서"));
        boolean asksForScheduleGapActivity = isScheduleGapActivityRequest(value);
        if (asksForActivityAfterMeal || asksForActivityAfterVisit || asksForScheduleGapActivity) {
            keywords.add("관광지");
        }
        if (asksForScheduleGapActivity) {
            keywords.add("공원");
            keywords.add("박물관");
        }
        if (value.contains("\uC1FC\uD551")) {
            keywords.add("\uC1FC\uD551");
        }
        if (value.contains("\uD328\uC158")) {
            keywords.add("\uD328\uC158");
        }
        if (value.contains("\uD3B8\uC9D1\uC0F5")) {
            keywords.add("\uD3B8\uC9D1\uC0F5");
        }
        return List.copyOf(keywords);
    }

    private static boolean isScheduleGapActivityRequest(String question) {
        String value = question == null ? "" : question;
        return value.contains("일정 중간") || value.contains("중간에 넣")
                || value.contains("일정 사이") || value.contains("사이 시간")
                || (value.contains("점심") && value.contains("저녁") && value.contains("사이"))
                || value.contains("빈 시간") || value.contains("남는 시간") || value.contains("여유 시간")
                || value.contains("시간 남") || value.contains("시간 때울") || value.contains("잠깐 갈")
                || value.contains("다음 코스") || value.contains("이후에 갈") || value.contains("이후 일정")
                || value.contains("이후 코스") || value.contains("다음 일정") || value.contains("남은 일정")
                || value.contains("가볼 만한 곳") || value.contains("갈 만한 곳")
                || value.contains("주변 볼거리") || value.contains("주변 놀거리");
    }

    private static boolean isFollowUpActivityRequest(String question) {
        String value = question == null ? "" : question;
        boolean hasFollowUpExpression = value.contains("먹고") || value.contains("먹은 후") || value.contains("식사 후")
                || value.contains("식사하고") || value.contains("갔다가") || value.contains("간 뒤") || value.contains("간 후")
                || value.contains("방문 후") || value.contains("방문하고") || value.contains("들르고")
                || value.contains("다녀와서") || value.contains("이후에") || value.contains("이후 일정");
        boolean asksForActivity = value.contains("뭐 할") || value.contains("뭐할") || value.contains("뭘 할")
                || value.contains("뭘할") || value.contains("무엇을 할") || value.contains("무엇을할")
                || value.contains("할 수 있는") || value.contains("할수 있는") || value.contains("할수있는")
                || value.contains("할만한") || value.contains("할 만한") || value.contains("갈 수 있는")
                || value.contains("갈수 있는") || value.contains("갈수있는") || value.contains("가볼") || value.contains("갈 만한")
                || value.contains("뭐가 있") || value.contains("무엇이 있") || value.contains("볼거리")
                || value.contains("놀거리") || value.contains("구경");
        return hasFollowUpExpression && asksForActivity;
    }

    private static boolean containsCafeIntent(String value) {
        return value.contains("카페") || value.contains("커피") || value.contains("로스터리")
                || value.contains("티하우스");
    }

    private static boolean containsBakeryIntent(String value) {
        return containsAny(value.toLowerCase(java.util.Locale.ROOT), BAKERY_TERMS);
    }

    private static boolean containsBarIntent(String value) {
        return containsAny(value.toLowerCase(java.util.Locale.ROOT), BAR_TERMS)
                || value.contains("클럽") || value.contains("유흥");
    }

    private static boolean containsRestaurantIntent(String value) {
        return value.contains("맛집") || value.contains("밥집") || value.contains("식당")
                || value.contains("음식점") || value.contains("먹거리") || value.contains("먹을 곳")
                || value.contains("식사할 곳") || value.contains("점심") || value.contains("저녁")
                || value.contains("밥") || value.contains("음식") || value.contains("한끼");
    }

    private static boolean containsAttractionIntent(String value) {
        return containsAny(value.toLowerCase(java.util.Locale.ROOT), ATTRACTION_TERMS);
    }

    private static boolean containsShoppingIntent(String value) {
        return containsAny(value.toLowerCase(java.util.Locale.ROOT), SHOPPING_TERMS);
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
        // 긴 자연어 질문 전체를 먼저 검색하면 요청 시간 예산을 소진해
        // "성수동 카페" 같은 실제 장소 검색까지 도달하지 못할 수 있다.
        // 지역 + 업종 검색을 우선해 검증 가능한 상호 후보를 먼저 확보한다.
        boolean hasDaySpecificKeywords = addDaySpecificKeywords(keywords, question);

        List<String> locations = extractLocations(question);
        if (locations.isEmpty() && destination != null && !destination.isBlank()) {
            locations = List.of(destination.trim());
        }
        // "둘째 날에 서귀포로 가는데 점심 맛집"처럼 DAY와 지역, 일반 식사 의도만
        // 있는 질문은 위 단계에서 "서귀포 맛집" 한 개만 남는다. Kakao 키워드
        // 검색에서 '맛집' 결과가 비어도 실제 음식점 후보를 확보할 수 있도록
        // DAY에서 읽은 지역에는 식당/음식점 검색을 즉시 보완한다. 자연어 위치
        // 추출은 조사 뒤 문장 일부를 추가 후보로 잡을 수 있으므로 DAY 위치를 우선한다.
        List<String> restaurantLocations = extractDayLocations(question);
        if (restaurantLocations.isEmpty() && locations.size() == 1) {
            restaurantLocations = locations;
        }
        if (containsRestaurantIntent(question) && !isScheduleGapActivityRequest(question)
                && restaurantLocations.size() == 1) {
            for (String location : restaurantLocations) {
                keywords.add(location + " 식당");
                keywords.add(location + " 음식점");
            }
        }
        addGenericShoppingKeywords(keywords, locations, question);
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
        // 일반 추천은 기존의 지역·업종 검색 순서를 유지한다. 다만 "다른 식당"
        // 요청에서는 첫 번째 맛집 결과만으로 후보가 소진될 수 있어, 같은 지역의
        // 식당/음식점 결과를 추가 후보로 확보한다.
        if (isAlternativeSearchQuery(question) && containsRestaurantIntent(question)
                && !isScheduleGapActivityRequest(question)) {
            for (String location : locations) {
                keywords.add(location + " 식당");
                keywords.add(location + " 음식점");
            }
        }
        if (!primary.isBlank() && primary.length() <= 40) {
            keywords.add(primary);
        }
        addDestinationScopedAlternativeKeywords(keywords, locations, destination, question);
        return keywords.stream().limit(MAX_KAKAO_SEARCHES_PER_QUESTION).toList();
    }

    private static boolean addDaySpecificKeywords(LinkedHashSet<String> keywords, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        List<String> commonCategories = extractCommonCategories(question);
        var matcher = DAY_LOCATION.matcher(question);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String location = trimLocationSuffix(matcher.group(1));
            String segment = question.substring(matcher.end(), nextDayMarkerStart(question, matcher.end()));
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

    private static List<String> extractDayLocations(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        collectMatches(locations, DAY_LOCATION, question);
        return locations.stream()
                .map(KakaoPlaceDiscoveryService::trimLocationSuffix)
                .filter(location -> !isNonLocationWord(location))
                .toList();
    }

    private static int nextDayMarkerStart(String question, int fromIndex) {
        var nextDay = DAY_MARKER.matcher(question);
        return nextDay.find(fromIndex) ? nextDay.start() : question.length();
    }

    private static List<String> extractLocations(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();

        // Preserve the day order first so DAY 1 through DAY N get fair search coverage.
        collectMatches(locations, DAY_LOCATION, question);
        // Administrative-area and station names: "성수동", "부산진구", "강남역".
        collectMatches(locations, Pattern.compile(
                "([가-힣0-9]+(?:특별시|광역시|특별자치시|특별자치도|도|시|군|구|동|읍|면|리|역))"
        ), question);
        // Natural Korean expressions: "성수에서", "여수 근처", "강릉의 카페".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})(?=\\s*(?:에서|에(?:서)?|으로|로|근처|주변|쪽|의))"
        ), question);
        // Linked locations: "성수와 연남에서", "여수, 순천 카페".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})(?=\\s*(?:와|과|,|·))"
        ), question);
        // Compact searches without a particle: "대구 카페", "전주 맛집".
        collectMatches(locations, Pattern.compile(
                "(?:^|\\s)([가-힣0-9]{2,12})\\s*(?=(?:카페|커피|맛집|식당|음식점|밥집|베이커리|빵집|제과점|도넛|케이크|술집|와인바|칵테일바|바|펍|클럽|놀거리|관광지|명소|공원|박물관|미술관|전시|체험|전망대|해수욕장|해변|야경|포토스팟|랜드마크|기념관|테마파크|놀이공원|아쿠아리움|동물원|수목원|식물원|오름|폭포|호수|둘레길|산책로|트레킹|등산|케이블카|스카이워크|공방|원데이클래스|액티비티|레저|서핑|요트|크루즈|자전거|방탈출|공연|극장|쇼핑|편집샵|소품샵|빈티지|의류|잡화|기념품|서점|레코드|스니커즈|시장|백화점|아울렛|플리마켓))"
        ), question);
        return locations.stream()
                .map(KakaoPlaceDiscoveryService::trimLocationSuffix)
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
                || value.equals("일정") || value.equals("추천") || value.equals("카페")
                || value.equals("커피") || value.equals("맛집") || value.equals("식당")
                || value.equals("음식점") || value.equals("밥집") || value.equals("베이커리")
                || value.equals("빵집") || value.equals("제과점") || value.equals("도넛")
                || value.equals("케이크") || value.equals("술집") || value.equals("와인바")
                || value.equals("칵테일바") || value.equals("펍") || value.equals("이자카야")
                || value.equals("놀거리") || value.equals("관광지") || value.equals("명소")
                || value.equals("공원") || value.equals("박물관") || value.equals("미술관")
                || value.equals("전시") || value.equals("체험") || value.equals("전망대")
                || value.equals("해수욕장") || value.equals("해변") || value.equals("야경")
                || value.equals("포토스팟") || value.equals("랜드마크") || value.equals("기념관")
                || value.equals("테마파크") || value.equals("놀이공원") || value.equals("아쿠아리움")
                || value.equals("동물원") || value.equals("수목원") || value.equals("식물원")
                || value.equals("오름") || value.equals("폭포") || value.equals("호수")
                || value.equals("둘레길") || value.equals("산책로") || value.equals("트레킹")
                || value.equals("등산") || value.equals("케이블카") || value.equals("스카이워크")
                || value.equals("공방") || value.equals("원데이클래스") || value.equals("액티비티")
                || value.equals("레저") || value.equals("서핑") || value.equals("요트")
                || value.equals("크루즈") || value.equals("자전거") || value.equals("방탈출")
                || value.equals("공연") || value.equals("극장") || value.equals("쇼핑") || value.equals("시장")
                || value.equals("편집샵") || value.equals("백화점") || value.equals("아울렛")
                || value.equals("소품샵") || value.equals("빈티지") || value.equals("의류")
                || value.equals("잡화") || value.equals("기념품") || value.equals("서점")
                || value.equals("레코드") || value.equals("스니커즈")
                || value.equals("플리마켓") || value.equals("근처") || value.equals("주변")
                || value.equals("여기") || value.equals("이곳") || value.equals("곳")
                || value.equals("곳도") || value.equals("와인")
                || value.equals("칵테일") || value.equals("lp") || value.equals("사")
                || value.equals("사이") || value.equals("시간")
                || isPartialVenueType(value);
    }

    // "아쿠아리움 체험"을 분석할 때 정규식의 짧은 매칭 결과인 "아쿠아리"를
    // 지역으로 오인하지 않도록, 알려진 업종어의 불완전한 접두어는 제외한다.
    private static boolean isPartialVenueType(String value) {
        if (value.length() < 2) {
            return false;
        }
        for (List<String> venueTerms : List.of(BAKERY_TERMS, BAR_TERMS, MEAL_TERMS, ATTRACTION_TERMS, SHOPPING_TERMS)) {
            if (venueTerms.stream().anyMatch(term -> term.startsWith(value) && !term.equals(value))) {
                return true;
            }
        }
        return false;
    }

    private static String trimLocationSuffix(String location) {
        return TRAILING_PARTICLE.matcher(location).replaceFirst("");
    }

    private static List<String> extractCategories(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<String> categories = new LinkedHashSet<>(extractCommonCategories(value));
        categories.addAll(extractSpecificCategories(value));
        return categories.isEmpty() ? List.of("장소") : List.copyOf(categories);
    }

    private static List<String> extractCommonCategories(String value) {
        List<String> categories = new ArrayList<>();
        if (containsBakeryIntent(value)) {
            categories.add("베이커리");
        } else if (containsCafeIntent(value)) {
            categories.add("카페");
        }
        if (containsRestaurantIntent(value) && !isScheduleGapActivityRequest(value)) {
            categories.add("맛집");
        }
        return categories;
    }

    private static List<String> extractSpecificCategories(String value) {
        List<String> categories = new ArrayList<>();
        if (value.contains("술집") || value.contains("바") || value.contains("펍")
                || value.contains("유흥") || value.contains("밤") || value.contains("호프")
                || value.contains("포차") || value.contains("이자카야") || value.contains("와인")
                || value.contains("칵테일") || value.contains("맥주")) {
            categories.add("술집");
        }
        if (value.contains("클럽")) {
            categories.add("클럽");
        }
        if (value.contains("놀거리") || value.contains("데이트") || value.contains("즐길")
                || value.contains("놀 수") || value.contains("할 수 있는") || value.contains("뭐 할")) {
            categories.add("놀거리");
            categories.add("관광지");
        }
        if (containsAttractionIntent(value) || value.contains("명소")) {
            categories.add("관광지");
        }
        if (value.contains("쇼핑") || value.contains("구매") || value.contains("편집샵")
                || value.contains("백화점") || value.contains("아울렛")) {
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
        if (value.contains("베이커리")) terms.add("베이커리");
        if (value.contains("빵집")) terms.add("빵집");
        if (value.contains("제과") || value.contains("제빵")) terms.add("제과점");
        if (value.contains("도넛")) terms.add("도넛");
        if (value.contains("케이크")) terms.add("케이크");
        if (value.contains("로스터리")) terms.add("로스터리");
        if (value.contains("커피")) terms.add("카페");
        if (value.contains("티하우스")) terms.add("티하우스");
        if (value.contains("이탈리안")) terms.add("이탈리안");
        if (value.contains("중식")) terms.add("중식");
        if (value.contains("일식")) terms.add("일식");
        if (value.contains("한식")) terms.add("한식");
        if (value.contains("국밥")) terms.add("국밥");
        if (value.contains("고기") || value.contains("삼겹살")) terms.add("고기");
        if (value.contains("분식")) terms.add("분식");
        if (value.contains("라멘")) terms.add("라멘");
        if (value.contains("파스타")) terms.add("파스타");
        if (value.contains("스시")) terms.add("스시");
        if (value.contains("이자카야")) terms.add("이자카야");
        if (value.contains("호프") || value.contains("맥주")) terms.add("호프");
        if (value.contains("포차")) terms.add("포차");
        if (value.contains("공원")) terms.add("공원");
        if (value.contains("박물관")) terms.add("박물관");
        if (value.contains("역사관")) terms.add("역사관");
        if (value.contains("미술관")) terms.add("미술관");
        if (value.contains("전시") || value.contains("갤러리")) terms.add("전시");
        if (value.contains("체험")) terms.add("체험");
        if (value.contains("해수욕장") || value.contains("바다")) terms.add("해수욕장");
        if (value.contains("전망대")) terms.add("전망대");
        if (value.contains("야경")) terms.add("야경");
        if (value.contains("포토스팟")) terms.add("포토스팟");
        if (value.contains("랜드마크")) terms.add("랜드마크");
        if (value.contains("기념관")) terms.add("기념관");
        if (value.contains("테마파크")) terms.add("테마파크");
        if (value.contains("놀이공원")) terms.add("놀이공원");
        if (value.contains("아쿠아리움")) terms.add("아쿠아리움");
        if (value.contains("동물원")) terms.add("동물원");
        if (value.contains("수목원")) terms.add("수목원");
        if (value.contains("식물원")) terms.add("식물원");
        if (value.contains("오름")) terms.add("오름");
        if (value.contains("폭포")) terms.add("폭포");
        if (value.contains("호수")) terms.add("호수");
        if (value.contains("둘레길") || value.contains("산책로")) terms.add("산책로");
        if (value.contains("트레킹") || value.contains("등산")) terms.add("트레킹");
        if (value.contains("케이블카")) terms.add("케이블카");
        if (value.contains("스카이워크")) terms.add("스카이워크");
        if (value.contains("공방")) terms.add("공방");
        if (value.contains("원데이클래스")) terms.add("원데이클래스");
        if (value.contains("액티비티") || value.contains("레저")) terms.add("액티비티");
        if (value.contains("서핑")) terms.add("서핑");
        if (value.contains("요트")) terms.add("요트");
        if (value.contains("크루즈")) terms.add("크루즈");
        if (value.contains("자전거")) terms.add("자전거");
        if (value.contains("방탈출")) terms.add("방탈출");
        if (value.contains("공연") || value.contains("극장")) terms.add("공연");
        if (value.contains("시장")) terms.add("시장");
        if (value.contains("플리마켓")) terms.add("플리마켓");
        if (value.contains("편집샵")) terms.add("편집샵");
        if (value.contains("소품샵")) terms.add("소품샵");
        if (value.contains("빈티지")) terms.add("빈티지샵");
        if (value.contains("의류")) terms.add("의류");
        if (value.contains("잡화")) terms.add("잡화");
        if (value.contains("기념품")) terms.add("기념품");
        if (value.contains("서점")) terms.add("서점");
        if (value.contains("레코드")) terms.add("레코드");
        if (value.contains("스니커즈")) terms.add("스니커즈");
        if (value.contains("백화점")) terms.add("백화점");
        if (value.contains("아울렛")) terms.add("아울렛");
        return List.copyOf(terms);
    }

    /**
     * 카카오 Local 검색은 "애월 와인바"처럼 짧은 지역·업종 조합에 강하지만,
     * 지역의 행정 단위가 빠진 검색은 같은 이름의 다른 지역 결과와 섞일 수 있다.
     * 단일 지역 질문에서만 여행지를 함께 붙인 업종 동의어를 추가해 실제 후보를
     * 넓힌다. 장소명을 코드에 고정하지 않고 모두 Kakao 응답만 사용한다.
     */
    private static void addDestinationScopedAlternativeKeywords(LinkedHashSet<String> keywords,
                                                                  List<String> locations,
                                                                  String destination,
                                                                  String question) {
        if (locations.size() != 1 || destination == null || destination.isBlank()) {
            return;
        }

        String trimmedDestination = destination.trim();
        String location = locations.getFirst();
        String normalizedDestination = trimmedDestination
                .replaceAll("(특별시|광역시|특별자치시|특별자치도)$", "");
        // 질문에 지역이 없어서 여행지 자체를 location으로 사용한 경우에는
        // "제주 제주 관광지"처럼 중복된 키워드를 만들지 않는다.
        String prefix = (location.equals(trimmedDestination) || location.equals(normalizedDestination))
                ? location + " "
                : trimmedDestination + " " + location + " ";
        for (String term : alternativeVenueSearchTerms(question)) {
            keywords.add(prefix + term);
        }
    }

    /**
     * 질문에 쓴 업종어가 검색 결과를 충분히 만들지 못할 때 사용할 실제 카카오
     * 키워드 동의어다. 업종을 섞지 않아 "식당" 요청에 베이커리가 포함되지 않고,
     * 관광·쇼핑도 각각의 후보군으로 확장된다.
     */
    private static List<String> alternativeVenueSearchTerms(String question) {
        String value = question == null ? "" : question;
        LinkedHashSet<String> terms = new LinkedHashSet<>();

        if (containsRestaurantIntent(value)) {
            terms.add("맛집");
            terms.add("식당");
            terms.add("음식점");
        }
        if (containsBakeryIntent(value)) {
            terms.add("베이커리");
            terms.add("빵집");
            terms.add("제과점");
            terms.add("도넛");
            terms.add("케이크");
        } else if (containsCafeIntent(value)) {
            terms.add("카페");
            terms.add("커피");
            terms.add("로스터리");
            terms.add("디저트카페");
        }
        if (containsBarIntent(value)) {
            terms.add("와인바");
            terms.add("칵테일바");
            terms.add("펍");
            terms.add("이자카야");
            terms.add("술집");
        }
        if (containsAttractionIntent(value) || value.contains("놀거리") || value.contains("데이트")
                || value.contains("즐길") || value.contains("놀 수") || value.contains("할 수 있는")
                || value.contains("뭐 할")) {
            terms.add("관광지");
            terms.add("명소");
            terms.add("체험");
            terms.add("공원");
            terms.add("박물관");
            terms.add("미술관");
            terms.add("전시");
            terms.add("전망대");
            terms.add("해수욕장");
            terms.add("야경");
            terms.add("포토스팟");
            terms.add("테마파크");
            terms.add("아쿠아리움");
            terms.add("수목원");
            terms.add("오름");
            terms.add("산책로");
            terms.add("체험");
        }
        if (containsShoppingIntent(value)) {
            terms.add("편집샵");
            terms.add("쇼핑");
            terms.add("시장");
            terms.add("백화점");
            terms.add("아울렛");
            terms.add("플리마켓");
            terms.add("소품샵");
            terms.add("빈티지샵");
            terms.add("의류");
            terms.add("잡화");
        }
        return List.copyOf(terms);
    }

    private static void addGenericShoppingKeywords(LinkedHashSet<String> keywords,
                                                    List<String> locations,
                                                    String question) {
        if (locations.isEmpty() || extractDayLocations(question).size() > 1
                || !containsShoppingIntent(question)
                || hasSpecificShoppingTerm(question)) {
            return;
        }

        // "전포에서 쇼핑할 곳"처럼 쇼핑만 말한 요청에는 카카오에서 상호 후보를
        // 잘 돌려주는 세부 업종을 먼저 검색한다. 여러 지역/DAY를 한 요청에 쓴
        // 경우까지 확장하면 호출 예산이 소진되므로 단일 지역 요청에만 적용한다.
        // 조사와 문장 끝의 "곳도" 같은 단어가 위치 추출 후보에 섞일 수 있으므로,
        // 여러 DAY가 명시되지 않은 단일 지역 질문은 첫 번째 실제 위치를 기준으로 한다.
        String location = locations.getFirst();
        keywords.add(location + " 편집샵");
        keywords.add(location + " 소품샵");
        keywords.add(location + " 빈티지샵");
    }

    private static boolean hasSpecificShoppingTerm(String question) {
        String value = question == null ? "" : question;
        return value.contains("편집샵") || value.contains("소품샵") || value.contains("빈티지")
                || value.contains("의류") || value.contains("잡화") || value.contains("기념품")
                || value.contains("서점") || value.contains("레코드") || value.contains("스니커즈")
                || value.contains("백화점") || value.contains("아울렛") || value.contains("시장")
                || value.contains("플리마켓");
    }

    private static boolean isAlternativeSearchQuery(String question) {
        String value = question == null ? "" : question.replaceAll("\\s+", "");
        return value.contains("다른") || value.contains("말고") || value.contains("재추천")
                || value.contains("다시추천") || value.contains("또추천");
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
