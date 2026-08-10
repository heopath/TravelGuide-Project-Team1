package org.example.all_my_trip_project.domain.accommodation.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationType;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한국관광공사 TourAPI 숙박정보 목록 provider.
 *
 * <p>{@code searchStay2}가 숙소명·주소·사진·좌표를 주지만 객실 요금은 주지 않는다.
 * 따라서 목록만 만들고 가격은 {@link AccommodationPriceSource#UNAVAILABLE}로 둔다.
 * 추후 LiteAPI 같은 PRICE provider가 붙으면 composite가 가격만 보강한다.
 */
@Slf4j
@Component
@Profile("!ui")
@Order(100)
public class TourApiAccommodationSearchProvider implements AccommodationSearchProvider {

    public static final String NAME = "tourapi";

    private static final String NORMAL_RESULT_CODE = "0000";
    private static final String LEGACY_NORMAL_RESULT_CODE = "00";
    private static final String ACCOMMODATION_CONTENT_TYPE = "32";

    private static final Map<String, Area> AREAS = areas();

    private record Area(String code, List<String> aliases) {}
    private record Destination(Area area, String district) {}
    private record DestinationMatch(Area area, String alias, int index) {}

    private final TourApiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Optional<String>> sigunguCodeCache = new ConcurrentHashMap<>();

    public TourApiAccommodationSearchProvider(TourApiProperties properties,
                                              RestClient.Builder restClientBuilder,
                                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AccommodationProviderRole role() {
        return AccommodationProviderRole.LISTING;
    }

    @Override
    public boolean supports(AccommodationSearchQuery query) {
        return properties.isConfigured()
                && query.destination() != null
                && !query.destination().isBlank();
    }

    @Override
    public List<AccommodationOffer> search(AccommodationSearchQuery query) {
        try {
            Optional<Destination> destination = destinationOf(query.destination());
            URI uri = destination
                    .map(value -> staySearchUri(value, resolveSigunguCode(value)))
                    .orElseGet(() -> keywordSearchUri(query.destination()));

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parse(body, query);
        } catch (RestClientResponseException exception) {
            // 예외 메시지에는 서비스키가 포함된 요청 URL이 들어갈 수 있어 상태 코드만 남긴다.
            log.warn("TourAPI 숙소 조회 HTTP 오류 status={} destination={}",
                    exception.getStatusCode().value(), query.destination());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("TourAPI 숙소 조회 실패 type={} destination={}",
                    exception.getClass().getSimpleName(), query.destination());
            return List.of();
        }
    }

    private URI staySearchUri(Destination destination, Optional<String> sigunguCode) {
        StringBuilder url = commonUrl("/searchStay2")
                .append("&arrange=A")
                .append("&areaCode=").append(destination.area().code());
        sigunguCode.ifPresent(code -> url.append("&sigunguCode=").append(code));
        return URI.create(url.toString());
    }

    /** 광역 지역을 알 수 없는 "강릉" 같은 입력은 숙박 콘텐츠 키워드 검색으로 처리한다. */
    private URI keywordSearchUri(String keyword) {
        return URI.create(commonUrl("/searchKeyword2")
                .append("&arrange=A")
                .append("&contentTypeId=").append(ACCOMMODATION_CONTENT_TYPE)
                .append("&keyword=").append(encode(keyword))
                .toString());
    }

    private Optional<String> resolveSigunguCode(Destination destination) {
        if (destination.district().isBlank()) {
            return Optional.empty();
        }
        String cacheKey = destination.area().code() + ":" + normalize(destination.district());
        return sigunguCodeCache.computeIfAbsent(cacheKey,
                ignored -> requestSigunguCode(destination.area().code(), destination.district()));
    }

    private Optional<String> requestSigunguCode(String areaCode, String district) {
        try {
            URI uri = URI.create(commonUrl("/areaCode2")
                    .append("&areaCode=").append(areaCode)
                    .toString());
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            String target = normalize(district);

            return items(body).stream()
                    .filter(item -> namesMatch(target, normalize(text(item, "name"))))
                    .map(item -> text(item, "code"))
                    .filter(code -> !code.isBlank())
                    .findFirst();
        } catch (RuntimeException exception) {
            log.info("TourAPI 시군구 코드 조회 실패로 광역 지역 전체를 검색합니다. areaCode={} type={}",
                    areaCode, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private StringBuilder commonUrl(String operation) {
        return new StringBuilder(properties.getBaseUrl()).append(operation)
                .append("?serviceKey=").append(encodedServiceKey())
                .append("&MobileOS=ETC")
                .append("&MobileApp=").append(encode(properties.getMobileApp()))
                .append("&_type=json")
                .append("&numOfRows=").append(properties.getMaxRows())
                .append("&pageNo=1");
    }

    private List<AccommodationOffer> parse(String body, AccommodationSearchQuery query) {
        List<JsonNode> items = items(body);
        List<AccommodationOffer> offers = new ArrayList<>(items.size());

        for (JsonNode item : items) {
            toOffer(item, query).ifPresent(offers::add);
        }
        return offers;
    }

    private List<JsonNode> items(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        if (body.stripLeading().startsWith("<")) {
            log.warn("TourAPI가 JSON 대신 XML 오류 응답을 반환했습니다.");
            return List.of();
        }

        try {
            JsonNode response = objectMapper.readTree(body).path("response");
            String resultCode = response.path("header").path("resultCode").asText();
            if (!resultCode.isBlank()
                    && !NORMAL_RESULT_CODE.equals(resultCode)
                    && !LEGACY_NORMAL_RESULT_CODE.equals(resultCode)) {
                log.warn("TourAPI 오류 응답 resultCode={} resultMsg={}", resultCode,
                        response.path("header").path("resultMsg").asText());
                return List.of();
            }

            JsonNode item = response.path("body").path("items").path("item");
            if (item.isArray()) {
                List<JsonNode> result = new ArrayList<>();
                item.forEach(result::add);
                return result;
            }
            return item.isObject() ? List.of(item) : List.of();
        } catch (Exception exception) {
            log.warn("TourAPI 숙소 응답 JSON 해석 실패 type={}",
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private Optional<AccommodationOffer> toOffer(JsonNode item, AccommodationSearchQuery query) {
        String contentId = text(item, "contentid");
        String name = text(item, "title");
        if (contentId.isBlank() || name.isBlank()) {
            return Optional.empty();
        }

        String address = joinAddress(text(item, "addr1"), text(item, "addr2"));
        String category = firstNonBlank(text(item, "lclsSystm3"), text(item, "cat3"));

        return Optional.of(new AccommodationOffer(
                NAME + ":" + contentId,
                NAME,
                name,
                accommodationType(category, name),
                areaLabel(address, query.destination()),
                address,
                null,
                null,
                null,
                null,
                query.currency(),
                AccommodationPriceSource.UNAVAILABLE,
                List.of(),
                false,
                false,
                blankToNull(firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"))),
                number(item, "mapy"),
                number(item, "mapx"),
                null,
                0.0
        ));
    }

    private AccommodationType accommodationType(String category, String name) {
        return switch (category) {
            case "B02010100" -> AccommodationType.HOTEL;
            case "B02010500" -> AccommodationType.RESORT;
            case "B02010600" -> AccommodationType.GUESTHOUSE;
            case "B02010700", "B02011100" -> AccommodationType.PENSION;
            case "B02010900" -> AccommodationType.MOTEL;
            case "B02011600" -> AccommodationType.HANOK;
            default -> typeFromName(name);
        };
    }

    private AccommodationType typeFromName(String name) {
        String normalized = normalize(name);
        if (normalized.contains("리조트") || normalized.contains("콘도")) return AccommodationType.RESORT;
        if (normalized.contains("펜션")) return AccommodationType.PENSION;
        if (normalized.contains("게스트하우스") || normalized.contains("호스텔")) return AccommodationType.GUESTHOUSE;
        if (normalized.contains("모텔")) return AccommodationType.MOTEL;
        if (normalized.contains("한옥")) return AccommodationType.HANOK;
        if (normalized.contains("호텔")) return AccommodationType.HOTEL;
        return AccommodationType.ETC;
    }

    private Optional<Destination> destinationOf(String rawDestination) {
        String normalized = normalize(rawDestination);
        DestinationMatch best = null;
        for (Area area : AREAS.values()) {
            for (String alias : area.aliases()) {
                String normalizedAlias = normalize(alias);
                int index = normalized.indexOf(normalizedAlias);
                if (index < 0) {
                    continue;
                }
                if (best == null || index < best.index()
                        || (index == best.index() && normalizedAlias.length() > best.alias().length())) {
                    best = new DestinationMatch(area, normalizedAlias, index);
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        String district = normalized.substring(best.index() + best.alias().length());
        return Optional.of(new Destination(best.area(), district));
    }

    private static Map<String, Area> areas() {
        Map<String, Area> result = new LinkedHashMap<>();
        result.put("1", new Area("1", List.of("서울특별시", "서울")));
        result.put("2", new Area("2", List.of("인천광역시", "인천")));
        result.put("3", new Area("3", List.of("대전광역시", "대전")));
        result.put("4", new Area("4", List.of("대구광역시", "대구")));
        result.put("5", new Area("5", List.of("광주광역시", "광주")));
        result.put("6", new Area("6", List.of("부산광역시", "부산")));
        result.put("7", new Area("7", List.of("울산광역시", "울산")));
        result.put("8", new Area("8", List.of("세종특별자치시", "세종")));
        result.put("31", new Area("31", List.of("경기도", "경기")));
        result.put("32", new Area("32", List.of("강원특별자치도", "강원도", "강원")));
        result.put("33", new Area("33", List.of("충청북도", "충북")));
        result.put("34", new Area("34", List.of("충청남도", "충남")));
        result.put("35", new Area("35", List.of("경상북도", "경북")));
        result.put("36", new Area("36", List.of("경상남도", "경남")));
        result.put("37", new Area("37", List.of("전북특별자치도", "전라북도", "전북")));
        result.put("38", new Area("38", List.of("전라남도", "전남")));
        result.put("39", new Area("39", List.of("제주특별자치도", "제주도", "제주")));
        return Map.copyOf(result);
    }

    private boolean namesMatch(String requested, String candidate) {
        return !requested.isBlank() && !candidate.isBlank()
                && (requested.contains(candidate) || candidate.contains(requested));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private Double number(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String joinAddress(String first, String second) {
        return (first + " " + second).trim();
    }

    private String areaLabel(String address, String fallback) {
        if (address.isBlank()) return fallback;
        String[] parts = address.split("\\s+");
        return parts.length < 2 ? parts[0] : parts[0] + " " + parts[1];
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    /** 포털의 인코딩 키와 디코딩 키 중 어느 것을 넣어도 한 번만 인코딩한다. */
    private String encodedServiceKey() {
        String key = properties.getServiceKey().trim();
        return key.contains("%") ? key : encode(key);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
