package org.example.all_my_trip_project.domain.place.service;

import com.sun.net.httpserver.HttpServer;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 매칭은 "엉뚱한 사진을 붙이지 않는 것"이 목적이라 경계를 고정해 둔다.
 *
 * <p>가짜 TourAPI를 로컬에 띄우고 실제 요청을 보낸다. 내부 메서드를 리플렉션으로 부르면
 * 두 단계가 어떻게 이어지는지, 1단계가 비었을 때 2단계로 넘어가는지를 볼 수 없다.
 */
class TourApiPlaceImageProviderTest {

    private static final BigDecimal LAT = new BigDecimal("35.1585232");
    private static final BigDecimal LON = new BigDecimal("129.1598547");
    /** 위 좌표에서 약 70m 떨어진 지점. 500m 안쪽이다. */
    private static final String NEAR_LAT = "35.15915";
    private static final String NEAR_LON = "129.15985";
    /** 강릉쯤. 100km 밖이다. */
    private static final String FAR_LAT = "37.7519";
    private static final String FAR_LON = "128.8761";

    private HttpServer server;
    private final AtomicReference<String> nearbyBody = new AtomicReference<>(emptyBody());
    private final AtomicReference<String> keywordBody = new AtomicReference<>(emptyBody());
    private final AtomicInteger keywordCalls = new AtomicInteger();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/KorService2/locationBasedList2", exchange -> respond(exchange, nearbyBody.get()));
        server.createContext("/KorService2/searchKeyword2", exchange -> {
            keywordCalls.incrementAndGet();
            respond(exchange, keywordBody.get());
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("좌표 반경 안에서 이름이 같으면 그 사진을 쓴다")
    void usesNearbyImageWhenNameMatches() {
        nearbyBody.set(body(item("해운대해수욕장", "https://img/haeundae.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("해운대해수욕장")).contains("https://img/haeundae.jpg");
        assertThat(keywordCalls).hasValue(0);
    }

    @Test
    @DisplayName("반경 안에 있어도 이름이 다르면 쓰지 않는다")
    void ignoresNearbyPlaceWithDifferentName() {
        nearbyBody.set(body(item("국립민속박물관", "https://img/folk.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("스타벅스 경복궁점")).isEmpty();
    }

    @Test
    @DisplayName("사진이 없는 항목은 후보에서 뺀다")
    void skipsItemsWithoutImage() {
        nearbyBody.set(body(item("해운대해수욕장", "", NEAR_LAT, NEAR_LON)));

        assertThat(find("해운대해수욕장")).isEmpty();
    }

    /*
     * 넓은 장소는 카카오 좌표와 관광공사 좌표가 떨어져 있어 반경 안에 안 잡힌다.
     * 이때 이름으로 찾고 좌표로 확인하는 2단계가 없으면 영영 사진이 없다.
     */
    @Test
    @DisplayName("좌표로 못 찾으면 이름으로 검색해 좌표로 확인한다")
    void fallsBackToKeywordSearch() {
        keywordBody.set(body(item("해운대해수욕장", "https://img/haeundae.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("해운대해수욕장")).contains("https://img/haeundae.jpg");
        assertThat(keywordCalls).hasValue(1);
    }

    /* "동백섬" ↔ "해운대 동백섬". 비율은 0.5뿐이지만 좌표가 붙어 있으면 같은 장소다. */
    @Test
    @DisplayName("아주 가까우면 이름이 조금 달라도 같은 장소로 본다")
    void acceptsWeakerNameMatchWhenVeryClose() {
        keywordBody.set(body(item("해운대 동백섬", "https://img/dongbaek.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("동백섬")).contains("https://img/dongbaek.jpg");
    }

    /* "남산공원"은 서울·강릉·고성·화순에 다 있다. 이름만으로는 고를 수 없다. */
    @Test
    @DisplayName("멀리 있는 같은 이름은 다른 장소로 본다")
    void rejectsSameNameFarAway() {
        keywordBody.set(body(item("강릉 남산공원", "https://img/gangneung.jpg", FAR_LAT, FAR_LON)));

        assertThat(find("남산공원")).isEmpty();
    }

    /*
     * 이름을 눅이는 것이 위험한 이유는 "경복궁"에 "경복궁역" 사진이 붙는 경우다.
     * 가장 닮은 것을 고르므로 진짜 경복궁이 함께 있으면 그쪽이 이긴다.
     */
    @Test
    @DisplayName("후보가 여럿이면 이름이 가장 닮은 것을 쓴다")
    void picksTheClosestNameAmongCandidates() {
        keywordBody.set(body(
                item("경복궁역", "https://img/station.jpg", NEAR_LAT, NEAR_LON),
                item("경복궁", "https://img/palace.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("경복궁")).contains("https://img/palace.jpg");
    }

    /* "한강"은 "더한강"에 0.67로 걸린다. 짧은 이름은 겹치는 글자가 적어 비율이 쉽게 오른다. */
    @Test
    @DisplayName("이름이 너무 짧으면 검색으로 확인하지 않는다")
    void doesNotSearchByVeryShortName() {
        keywordBody.set(body(item("더한강", "https://img/restaurant.jpg", NEAR_LAT, NEAR_LON)));

        assertThat(find("한강")).isEmpty();
        assertThat(keywordCalls).hasValue(0);
    }

    @Test
    @DisplayName("서비스키가 없으면 호출하지 않는다")
    void doesNothingWithoutServiceKey() {
        TourApiProperties properties = new TourApiProperties();
        properties.setBaseUrl(baseUrl());

        assertThat(new TourApiPlaceImageProvider(properties).findImageUrl("경복궁", LAT, LON)).isEmpty();
        assertThat(keywordCalls).hasValue(0);
    }

    private Optional<String> find(String placeName) {
        TourApiProperties properties = new TourApiProperties();
        properties.setBaseUrl(baseUrl());
        properties.setServiceKey("test-key");
        return new TourApiPlaceImageProvider(properties).findImageUrl(placeName, LAT, LON);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/KorService2";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json)
            throws java.io.IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static String item(String title, String image, String latitude, String longitude) {
        return "{\"title\":\"" + title + "\",\"firstimage\":\"" + image + "\","
                + "\"mapy\":\"" + latitude + "\",\"mapx\":\"" + longitude + "\"}";
    }

    private static String body(String... items) {
        return "{\"response\":{\"body\":{\"items\":{\"item\":[" + String.join(",", items) + "]}}}}";
    }

    private static String emptyBody() {
        return "{\"response\":{\"body\":{\"items\":\"\"}}}";
    }
}
