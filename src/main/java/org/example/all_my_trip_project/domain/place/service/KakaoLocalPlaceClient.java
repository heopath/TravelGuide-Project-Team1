package org.example.all_my_trip_project.domain.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Kakao Local REST API의 검색 결과를 내부 PlaceDTO로만 변환한다. */
@Slf4j
@Component
@Profile("!ui")
public class KakaoLocalPlaceClient {

    private static final URI KEYWORD_SEARCH_URI = URI.create("https://dapi.kakao.com/v2/local/search/keyword.json");
    private static final URI CATEGORY_SEARCH_URI = URI.create("https://dapi.kakao.com/v2/local/search/category.json");
    private static final int MAX_SIZE = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String restApiKey;
    private final Duration timeout;

    @Autowired
    public KakaoLocalPlaceClient(
            @Value("${kakao.local.rest-api-key:}") String restApiKey,
            @Value("${kakao.local.timeout-millis:5000}") long timeoutMillis
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                new ObjectMapper(), restApiKey, Duration.ofMillis(timeoutMillis));
    }

    KakaoLocalPlaceClient(HttpClient httpClient, ObjectMapper objectMapper, String restApiKey, Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.restApiKey = restApiKey;
        this.timeout = timeout;
    }

    public List<PlaceDTO> search(String keyword) {
        return search(keyword, timeout);
    }

    public List<PlaceDTO> search(String keyword, Duration requestTimeout) {
        if (restApiKey == null || restApiKey.isBlank() || keyword == null || keyword.isBlank()) {
            if (restApiKey == null || restApiKey.isBlank()) {
                log.warn("Kakao Local place search skipped because KAKAO_REST_API_KEY is not configured.");
            }
            return List.of();
        }

        Duration effectiveTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? timeout
                : requestTimeout.compareTo(timeout) < 0 ? requestTimeout : timeout;

        URI uri = UriComponentsBuilder.fromUri(KEYWORD_SEARCH_URI)
                .queryParam("query", keyword.trim())
                .queryParam("size", MAX_SIZE)
                .build()
                .encode()
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(effectiveTimeout)
                .header("Authorization", "KakaoAK " + restApiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Kakao Local place search failed. status={}, response={}",
                        response.statusCode(), abbreviate(response.body()));
                return List.of();
            }
            return parsePlaces(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kakao Local place search was interrupted.", exception);
            return List.of();
        } catch (IOException | RuntimeException exception) {
            log.warn("Kakao Local place search failed.", exception);
            return List.of();
        }
    }

    /**
     * Searches a keyword around a known point. Kakao category groups do not cover
     * shopping and fashion venues, so those requests must use the keyword API.
     */
    public List<PlaceDTO> searchNearby(String keyword, BigDecimal longitude, BigDecimal latitude,
                                       int radiusMeters, Duration requestTimeout) {
        if (restApiKey == null || restApiKey.isBlank() || keyword == null || keyword.isBlank()
                || longitude == null || latitude == null) {
            return List.of();
        }

        URI uri = UriComponentsBuilder.fromUri(KEYWORD_SEARCH_URI)
                .queryParam("query", keyword.trim())
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", Math.max(1, Math.min(radiusMeters, 20_000)))
                .queryParam("size", MAX_SIZE)
                .build()
                .encode()
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(effectiveTimeout(requestTimeout))
                .header("Authorization", "KakaoAK " + restApiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Kakao Local nearby keyword search failed. status={}, response={}",
                        response.statusCode(), abbreviate(response.body()));
                return List.of();
            }
            return parsePlaces(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kakao Local nearby keyword search was interrupted.", exception);
            return List.of();
        } catch (IOException | RuntimeException exception) {
            log.warn("Kakao Local nearby keyword search failed.", exception);
            return List.of();
        }
    }

    /**
     * 기준 장소의 좌표 주변에서 카카오 카테고리로 실제 장소를 찾는다.
     * 예: "이재모피자 본점 근처 카페" 요청은 기준 장소를 먼저 찾은 뒤 CE7 카테고리를 조회한다.
     */
    public List<PlaceDTO> searchByCategory(String categoryGroupCode, BigDecimal longitude, BigDecimal latitude,
                                           Duration requestTimeout) {
        return searchByCategory(categoryGroupCode, longitude, latitude, 2_000, requestTimeout);
    }

    public List<PlaceDTO> searchByCategory(String categoryGroupCode, BigDecimal longitude, BigDecimal latitude,
                                           int radiusMeters, Duration requestTimeout) {
        if (restApiKey == null || restApiKey.isBlank() || categoryGroupCode == null || categoryGroupCode.isBlank()
                || longitude == null || latitude == null) {
            return List.of();
        }

        Duration effectiveTimeout = effectiveTimeout(requestTimeout);
        URI uri = UriComponentsBuilder.fromUri(CATEGORY_SEARCH_URI)
                .queryParam("category_group_code", categoryGroupCode.trim())
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", Math.max(1, Math.min(radiusMeters, 20_000)))
                .queryParam("size", MAX_SIZE)
                .build()
                .encode()
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(effectiveTimeout)
                .header("Authorization", "KakaoAK " + restApiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Kakao Local category search failed. status={}, response={}",
                        response.statusCode(), abbreviate(response.body()));
                return List.of();
            }
            return parsePlaces(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Kakao Local category search was interrupted.", exception);
            return List.of();
        } catch (IOException | RuntimeException exception) {
            log.warn("Kakao Local category search failed.", exception);
            return List.of();
        }
    }

    private Duration effectiveTimeout(Duration requestTimeout) {
        return requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? timeout
                : requestTimeout.compareTo(timeout) < 0 ? requestTimeout : timeout;
    }

    static List<PlaceDTO> parsePlaces(JsonNode root) {
        JsonNode documents = root.path("documents");
        if (!documents.isArray()) {
            return List.of();
        }
        List<PlaceDTO> places = new ArrayList<>();
        for (JsonNode document : documents) {
            String id = text(document, "id");
            String name = text(document, "place_name");
            if (id.isBlank() || name.isBlank()) {
                continue;
            }
            String address = firstNonBlank(text(document, "road_address_name"), text(document, "address_name"));
            String region = region(address);
            places.add(PlaceDTO.builder()
                    .externalProvider("KAKAO")
                    .externalPlaceId(id)
                    .category(category(document))
                    .name(name)
                    .countryCode("KR")
                    .region(region)
                    .city(region)
                    .address(address)
                    .latitude(decimal(document, "y"))
                    .longitude(decimal(document, "x"))
                    .phone(text(document, "phone"))
                    .websiteUrl(text(document, "place_url"))
                    .active(true)
                    .build());
        }
        return List.copyOf(places);
    }

    private static String category(JsonNode document) {
        return switch (text(document, "category_group_code")) {
            case "CE7" -> "CAFE";
            case "FD6" -> "RESTAURANT";
            case "AD5" -> "ACCOMMODATION";
            case "AT4" -> "ATTRACTION";
            // places.category has a fixed DB check constraint. Kakao may return
            // ungrouped, shopping, or culture venues, so store those as an allowed
            // general travel category rather than the unsupported PLACE value.
            default -> "ATTRACTION";
        };
    }

    private static BigDecimal decimal(JsonNode document, String field) {
        String value = text(document, field);
        try {
            return value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String region(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        return tokens.length == 0 ? null : tokens[0];
    }

    private static String text(JsonNode document, String field) {
        return document.path(field).asText("").trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String abbreviate(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }
        return responseBody.length() <= 500 ? responseBody : responseBody.substring(0, 500) + "...";
    }
}
