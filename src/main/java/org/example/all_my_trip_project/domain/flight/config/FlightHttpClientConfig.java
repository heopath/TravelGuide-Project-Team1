package org.example.all_my_trip_project.domain.flight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 항공 provider가 쓰는 HTTP 클라이언트.
 *
 * <p>이 프로젝트에는 {@code spring-boot-restclient}가 런타임 클래스패스에 없어서
 * {@link RestClient.Builder}가 자동 설정되지 않는다. 그래서 직접 만들어 준다.
 *
 * <p>{@code WeatherService}처럼 {@code RestClient.create()}를 호출부에서 직접 쓰지 않은 이유는
 * 두 가지다. 첫째, 타임아웃 없는 외부 호출은 상대가 응답하지 않을 때 요청 스레드를 붙잡는다.
 * 둘째, 빌더를 주입받아야 테스트에서 {@code MockRestServiceServer}로 응답을 갈아끼울 수 있다.
 */
@Configuration
@Profile("!ui")
public class FlightHttpClientConfig {

    /** TAGO 평균 응답이 500ms다. 이보다 오래 걸리면 붙잡고 있을 이유가 없다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient.Builder flightRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
