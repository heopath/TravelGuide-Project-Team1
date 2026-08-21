package org.example.all_my_trip_project.domain.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * 카카오로 담은 장소의 대표 이미지를 한국관광공사 TourAPI에서 찾는다.
 *
 * <p>카카오 로컬 검색 응답에는 사진이 없다. 그래서 사용자가 일정에 담은 장소는
 * 대표 이미지가 비어 있고, 찜 목록과 일정 카드가 빈 칸으로 보인다.
 *
 * <p>이미지를 만들어내지 않는다. 공공기관이 그 장소에 대해 제공하는 사진만 쓰고,
 * 같은 장소라고 확신할 수 없으면 넣지 않는다. 엉뚱한 사진이 붙는 것보다 없는 편이 낫다.
 *
 * <p>두 단계로 찾는다. 좌표 주변부터 보고, 없으면 이름으로 검색한 뒤 좌표로 확인한다.
 * 좌표만으로는 부족하기 때문이다 — 해운대해수욕장이나 경복궁처럼 넓은 장소는 카카오가
 * 찍은 좌표와 관광공사가 등록한 좌표가 수백 미터 떨어져 있어 반경 안에 아예 안 잡힌다.
 * 실제 장소 70곳으로 재어 보니 1단계만으로는 12곳, 2단계까지 하면 30곳이 채워졌다.
 */
@Component
@Profile("!ui")
@Slf4j
public class TourApiPlaceImageProvider {

    /** 1단계 좌표 반경(m). 좁게 잡아 다른 장소가 잡히지 않게 한다. */
    private static final int RADIUS_METERS = 300;
    /** 1단계에서 이름이 이만큼 겹쳐야 같은 장소로 본다. */
    private static final double NAME_MATCH_THRESHOLD = 0.6;

    /*
     * 2단계는 이름으로 찾은 뒤 좌표로 확인한다. 거리가 이름보다 믿을 만하다.
     * "남산공원"은 강릉·고성·화순에도 있어 이름만으로는 고를 수 없지만, 좌표를 보면
     * 서울 것만 438m고 나머지는 100km 밖이다. 그래서 가까울수록 이름 조건을 눅인다.
     */
    /** 이 거리 안이면 이름이 조금 달라도 같은 장소로 본다("동백섬" ↔ "해운대 동백섬"). */
    private static final int NEAR_METERS = 500;
    private static final double NEAR_NAME_THRESHOLD = 0.5;
    /** 더 멀면 이름이 확실해야 한다. 넓은 장소의 대표 좌표가 떨어져 있는 경우까지만 받는다. */
    private static final int FAR_METERS = 2000;
    private static final double FAR_NAME_THRESHOLD = NAME_MATCH_THRESHOLD;
    /**
     * 이름이 이보다 짧으면 검색으로 확인할 수 없다. "한강"은 "더한강"에 0.67로 걸린다.
     * 짧은 이름은 겹치는 글자 수가 적어 비율이 쉽게 올라간다.
     */
    private static final int MIN_SEARCHABLE_NAME_LENGTH = 3;

    private static final int EARTH_RADIUS_METERS = 6_371_000;

    /* 장소를 담는 흐름 안에서 부르므로 오래 기다리지 않는다. 못 받으면 이미지 없이 넘어간다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final TourApiProperties properties;
    private final RestClient restClient;
    // Spring Boot 4는 Jackson 3을 자동 구성하므로 Jackson 2 ObjectMapper를 주입받지 않는다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TourApiPlaceImageProvider(TourApiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 그 장소의 대표 이미지 URL을 돌려준다.
     * 키가 없거나, 후보가 없거나, 같은 장소라고 확신할 수 없으면 비어 있는 값을 준다.
     */
    public Optional<String> findImageUrl(String placeName, BigDecimal latitude, BigDecimal longitude) {
        if (!properties.isConfigured() || placeName == null || placeName.isBlank()
                || latitude == null || longitude == null) {
            return Optional.empty();
        }

        Optional<String> nearby = nearbyImage(placeName, latitude, longitude);
        if (nearby.isPresent()) return nearby;
        // 1단계가 통신 오류로 실패했더라도 2단계는 따로 시도한다.
        return searchedImage(placeName, latitude, longitude);
    }

    /** 1단계: 좌표 반경 안에서 이름이 충분히 닮은 것을 찾는다. */
    private Optional<String> nearbyImage(String placeName, BigDecimal latitude, BigDecimal longitude) {
        try {
            String body = get(locationBasedUri(latitude, longitude));
            String target = normalize(placeName);
            String bestImage = null;
            double bestScore = 0;

            for (JsonNode item : items(body)) {
                String image = imageOf(item);
                if (image.isBlank()) continue;
                double score = nameSimilarity(target, normalize(text(item, "title")));
                if (score > bestScore) {
                    bestScore = score;
                    bestImage = image;
                }
            }
            return bestScore >= NAME_MATCH_THRESHOLD ? Optional.ofNullable(bestImage) : Optional.empty();
        } catch (Exception exception) {
            warn("좌표 조회", exception, placeName);
            return Optional.empty();
        }
    }

    /**
     * 2단계: 이름으로 검색하고 좌표로 같은 장소인지 확인한다.
     *
     * <p>여러 개가 조건을 통과하면 이름이 가장 닮은 것을, 같으면 가까운 것을 쓴다.
     */
    private Optional<String> searchedImage(String placeName, BigDecimal latitude, BigDecimal longitude) {
        String target = normalize(placeName);
        if (target.length() < MIN_SEARCHABLE_NAME_LENGTH) return Optional.empty();

        try {
            String body = get(keywordSearchUri(placeName));
            String bestImage = null;
            double bestScore = 0;
            double bestDistance = Double.MAX_VALUE;

            for (JsonNode item : items(body)) {
                String image = imageOf(item);
                if (image.isBlank()) continue;

                double score = nameSimilarity(target, normalize(text(item, "title")));
                double distance = distanceMeters(latitude, longitude,
                        text(item, "mapy"), text(item, "mapx"));
                if (!samePlace(score, distance)) continue;

                if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                    bestScore = score;
                    bestDistance = distance;
                    bestImage = image;
                }
            }
            return Optional.ofNullable(bestImage);
        } catch (Exception exception) {
            warn("이름 검색", exception, placeName);
            return Optional.empty();
        }
    }

    /** 가까우면 이름을 눅이고, 멀면 이름이 확실해야 한다. */
    private boolean samePlace(double score, double distanceMeters) {
        if (distanceMeters <= NEAR_METERS) return score >= NEAR_NAME_THRESHOLD;
        return distanceMeters <= FAR_METERS && score >= FAR_NAME_THRESHOLD;
    }

    /** 좌표를 못 읽으면 같은 장소라고 볼 근거가 없다. 무한대로 두어 떨어뜨린다. */
    private double distanceMeters(BigDecimal latitude, BigDecimal longitude,
                                  String itemLatitude, String itemLongitude) {
        double lat2;
        double lon2;
        try {
            lat2 = Double.parseDouble(itemLatitude);
            lon2 = Double.parseDouble(itemLongitude);
        } catch (NumberFormatException exception) {
            return Double.MAX_VALUE;
        }

        double lat1 = Math.toRadians(latitude.doubleValue());
        double deltaLat = Math.toRadians(lat2 - latitude.doubleValue());
        double deltaLon = Math.toRadians(lon2 - longitude.doubleValue());
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    /*
     * URI를 직접 만들어 넘긴다. 문자열을 넘기면 RestClient가 URI 템플릿으로 보고
     * 한 번 더 인코딩해서, 이미 인코딩된 서비스키의 %2F가 %252F가 된다.
     * 그러면 공공데이터포털이 키를 못 알아보고 403 Forbidden을 준다.
     * 숙소 조회(TourApiAccommodationSearchProvider)가 URI를 넘기는 이유도 같다.
     */
    private String get(String url) {
        return restClient.get().uri(URI.create(url)).retrieve().body(String.class);
    }

    private String locationBasedUri(BigDecimal latitude, BigDecimal longitude) {
        return commonUrl("/locationBasedList2")
                + "&arrange=E"                 // 거리순
                + "&mapX=" + longitude.toPlainString()
                + "&mapY=" + latitude.toPlainString()
                + "&radius=" + RADIUS_METERS;
    }

    private String keywordSearchUri(String placeName) {
        return commonUrl("/searchKeyword2")
                + "&arrange=A"
                + "&keyword=" + encode(placeName);
    }

    private String commonUrl(String path) {
        return properties.getBaseUrl() + path
                + "?serviceKey=" + encodedServiceKey()
                + "&MobileOS=ETC"
                + "&MobileApp=" + encode(properties.getMobileApp())
                + "&_type=json"
                + "&numOfRows=20&pageNo=1";
    }

    private Iterable<JsonNode> items(String body) throws Exception {
        JsonNode items = objectMapper.readTree(body)
                .path("response").path("body").path("items").path("item");
        return items.isArray() ? items : java.util.List.of();
    }

    private String imageOf(JsonNode item) {
        return firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"));
    }

    /*
     * 통신 실패든 응답 파싱 실패든 결과는 같다. 이미지 없이 넘어간다.
     * 이미지가 없다고 장소 저장이 실패하면 안 된다. 조용히 포기한다.
     * 예외 메시지에는 서비스키가 든 URL이 섞일 수 있어 타입만 남긴다.
     */
    private void warn(String stage, Exception exception, String placeName) {
        log.warn("TourAPI 장소 이미지 {} 실패 type={} place={}",
                stage, exception.getClass().getSimpleName(), placeName);
    }

    /**
     * 한쪽 이름이 다른 쪽에 들어 있으면 짧은 쪽 길이를 기준으로 비율을 낸다.
     * "경복궁"과 "경복궁 근정전"처럼 표기가 조금 다른 경우를 같은 장소로 본다.
     */
    private double nameSimilarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0;
        if (left.equals(right)) return 1;
        String shorter = left.length() <= right.length() ? left : right;
        String longer = shorter.equals(left) ? right : left;
        return longer.contains(shorter) ? (double) shorter.length() / longer.length() : 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    /** 포털의 인코딩 키와 디코딩 키 중 어느 것을 넣어도 한 번만 인코딩한다. */
    private String encodedServiceKey() {
        String key = properties.getServiceKey().trim();
        return key.contains("%") ? key : encode(key);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
