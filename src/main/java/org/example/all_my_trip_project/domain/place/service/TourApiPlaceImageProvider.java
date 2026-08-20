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
 */
@Component
@Profile("!ui")
@Slf4j
public class TourApiPlaceImageProvider {

    /** 좌표 반경(m). 좁게 잡아 다른 장소가 잡히지 않게 한다. */
    private static final int RADIUS_METERS = 300;
    /** 이름이 이만큼 겹쳐야 같은 장소로 본다. */
    private static final double NAME_MATCH_THRESHOLD = 0.6;

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
     * 좌표 주변에서 이름이 충분히 일치하는 관광정보를 찾아 대표 이미지 URL을 돌려준다.
     * 키가 없거나, 후보가 없거나, 이름이 충분히 닮지 않으면 비어 있는 값을 준다.
     */
    public Optional<String> findImageUrl(String placeName, BigDecimal latitude, BigDecimal longitude) {
        /*
         * 왜 이미지가 안 붙었는지 알 수 없으면 고칠 수도 없다. 실패한 이유를 남긴다.
         * 조용히 비워두면 "키가 없는 것"과 "이름이 안 맞은 것"을 구분할 수 없다.
         */
        if (!properties.isConfigured()) {
            log.info("TourAPI 서비스키가 없어 장소 이미지를 채우지 않는다. place={}", placeName);
            return Optional.empty();
        }
        if (placeName == null || placeName.isBlank() || latitude == null || longitude == null) {
            log.info("이름이나 좌표가 없어 장소 이미지를 찾지 않는다. place={}", placeName);
            return Optional.empty();
        }

        try {
            String body = restClient.get().uri(locationBasedUri(latitude, longitude))
                    .retrieve().body(String.class);
            return bestImage(body, placeName);
        } catch (Exception exception) {
            // 통신 실패든 응답 파싱 실패든 결과는 같다. 이미지 없이 넘어간다.
            // 이미지가 없다고 장소 저장이 실패하면 안 된다. 조용히 포기한다.
            // 예외 메시지에는 서비스키가 든 URL이 섞일 수 있어 타입만 남긴다.
            log.warn("TourAPI 장소 이미지 조회 실패 type={} place={}",
                    exception.getClass().getSimpleName(), placeName);
            return Optional.empty();
        }
    }

    private String locationBasedUri(BigDecimal latitude, BigDecimal longitude) {
        return properties.getBaseUrl() + "/locationBasedList2"
                + "?serviceKey=" + encodedServiceKey()
                + "&MobileOS=ETC"
                + "&MobileApp=" + encode(properties.getMobileApp())
                + "&_type=json"
                + "&numOfRows=20&pageNo=1"
                + "&arrange=E"                 // 거리순
                + "&mapX=" + longitude.toPlainString()
                + "&mapY=" + latitude.toPlainString()
                + "&radius=" + RADIUS_METERS;
    }

    private Optional<String> bestImage(String body, String placeName) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String resultCode = root.path("response").path("header").path("resultCode").asText("");
        JsonNode items = root.path("response").path("body").path("items").path("item");

        if (!items.isArray()) {
            // 정상 응답이어도 주변에 관광정보가 없으면 items가 빈 문자열로 온다.
            // 키가 잘못됐을 때도 여기로 오므로 resultCode를 함께 남긴다.
            log.info("TourAPI 후보 없음. resultCode={} place={}", resultCode, placeName);
            return Optional.empty();
        }

        String target = normalize(placeName);
        String bestImage = null;
        String bestTitle = null;
        double bestScore = 0;
        int withImage = 0;

        for (JsonNode item : items) {
            String image = firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"));
            if (image.isBlank()) continue;
            withImage++;

            double score = nameSimilarity(target, normalize(text(item, "title")));
            if (score > bestScore) {
                bestScore = score;
                bestImage = image;
                bestTitle = text(item, "title");
            }
        }

        boolean matched = bestScore >= NAME_MATCH_THRESHOLD;
        log.info("TourAPI 이미지 매칭 place={} 후보={}건(사진있음 {}) 최고={} 점수={} 채택={}",
                placeName, items.size(), withImage, bestTitle, String.format("%.2f", bestScore), matched);
        return matched ? Optional.ofNullable(bestImage) : Optional.empty();
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
