package org.example.all_my_trip_project.domain.accommodation.provider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** LiteAPI 요금 조회가 항공 API의 짧은 HTTP 제한 시간을 공유하지 않게 한다. */
@Configuration
@Profile("!ui")
public class LiteApiHttpClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RESPONSE_MARGIN = Duration.ofSeconds(3);

    @Bean
    @Qualifier("liteApiRestClient")
    public RestClient liteApiRestClient(LiteApiSandboxProperties properties) {
        /*
         * LiteAPI 요청 본문에도 timeoutSeconds를 보낸다. HTTP 클라이언트가 그보다 먼저
         * 끊으면 외부 서버가 정상적으로 제한 시간 응답을 만들 기회가 없다. 운영에서 실제로
         * 항공용 5초 제한이 먼저 걸려 가격이 전부 사라졌다.
         */
        Duration providerTimeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(providerTimeout.plus(RESPONSE_MARGIN));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
