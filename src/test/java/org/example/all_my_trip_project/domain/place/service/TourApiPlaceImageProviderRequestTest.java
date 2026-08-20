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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 HTTP 요청을 받아 보고 무엇이 나갔는지 확인한다.
 *
 * <p>서비스키가 어떻게 나가는지는 목으로 볼 수 없다. 공공데이터포털에서 받는 키에는
 * {@code %2F}, {@code %3D}가 들어 있는데, 요청 URL을 문자열로 넘기면 RestClient가
 * URI 템플릿으로 보고 한 번 더 인코딩해 {@code %252F}가 된다. 코드는 멀쩡해 보이고
 * 테스트도 통과하지만 서버는 403 Forbidden을 준다. 실제로 겪었다.
 *
 * <p>바깥으로 나가지 않는다. 로컬 루프백에 임시 서버를 띄워 받은 쿼리를 그대로 본다.
 */
class TourApiPlaceImageProviderRequestTest {

    /** 포털이 주는 인코딩 키 그대로. %2F와 %3D가 살아 있어야 한다. */
    private static final String ENCODED_KEY = "abc%2Fdef%2Bghi%3D%3D";

    private HttpServer server;
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/KorService2/locationBasedList2", exchange -> {
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[
                      {"title":"경복궁","firstimage":"https://tong.example/gyeongbok.jpg"}
                    ]}}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("이미 인코딩된 서비스키를 다시 인코딩하지 않는다")
    void doesNotDoubleEncodeServiceKey() {
        Optional<String> image = provider().findImageUrl(
                "경복궁", new BigDecimal("37.5796"), new BigDecimal("126.9770"));

        assertThat(receivedQuery.get())
                .as("포털이 준 키가 그대로 나가야 한다. %%252F가 보이면 서버가 403을 준다.")
                .contains("serviceKey=" + ENCODED_KEY)
                .doesNotContain("%252F", "%253D");
        assertThat(image).contains("https://tong.example/gyeongbok.jpg");
    }

    @Test
    @DisplayName("좌표와 반경을 그대로 실어 보낸다")
    void sendsCoordinates() {
        provider().findImageUrl("경복궁", new BigDecimal("37.5796"), new BigDecimal("126.9770"));

        assertThat(receivedQuery.get())
                .contains("mapY=37.5796")
                .contains("mapX=126.9770")
                .contains("radius=300");
    }

    private TourApiPlaceImageProvider provider() {
        TourApiProperties properties = new TourApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/KorService2");
        properties.setServiceKey(ENCODED_KEY);
        return new TourApiPlaceImageProvider(properties);
    }
}
