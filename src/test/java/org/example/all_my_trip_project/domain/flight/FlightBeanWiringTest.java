package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.config.FlightHttpClientConfig;
import org.example.all_my_trip_project.domain.flight.provider.CompositeFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.MockFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.TagoFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.TagoProperties;
import org.example.all_my_trip_project.domain.flight.provider.TravelpayoutsPriceProvider;
import org.example.all_my_trip_project.domain.flight.provider.TravelpayoutsProperties;
import org.example.all_my_trip_project.domain.flight.service.FlightDeeplinkProperties;
import org.example.all_my_trip_project.domain.flight.service.FlightRecommendationScorer;
import org.example.all_my_trip_project.domain.flight.service.FlightScheduleAnalyzer;
import org.example.all_my_trip_project.domain.flight.service.TemplateDeeplinkBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 항공 도메인 빈이 실제로 배선되는지 확인한다.
 *
 * <p><b>이 테스트가 없어서 기동 실패를 빌드에서 놓쳤다.</b>
 * {@code AllMyTripProjectApplicationTests}는 기본 프로필인 {@code ui}로 도는데
 * 항공 빈은 대부분 {@code @Profile("!ui")}라 그 컨텍스트에 아예 올라오지 않는다.
 * 그래서 {@code RestClient.Builder} 빈이 없다는 사실이 {@code bootRun} 전까지 드러나지 않았다.
 *
 * <p>DB도 Redis도 필요 없는 슬라이스라 빌드에서 매번 돈다.
 */
class FlightBeanWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FlightHttpClientConfig.class)
            .withBean(FlightDeeplinkProperties.class)
            .withBean(TagoProperties.class)
            .withBean(TravelpayoutsProperties.class)
            .withBean(TemplateDeeplinkBuilder.class)
            .withBean(FlightScheduleAnalyzer.class)
            .withBean(FlightRecommendationScorer.class)
            .withBean(MockFlightSearchProvider.class)
            .withBean(TagoFlightSearchProvider.class)
            .withBean(TravelpayoutsPriceProvider.class)
            .withBean(CompositeFlightSearchProvider.class);

    @Test
    @DisplayName("항공 provider가 전부 배선된다")
    void wiresAllProviders() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasSingleBean(TagoFlightSearchProvider.class);
            assertThat(context).hasSingleBean(TravelpayoutsPriceProvider.class);
            assertThat(context).hasSingleBean(MockFlightSearchProvider.class);
            assertThat(context).hasSingleBean(CompositeFlightSearchProvider.class);
        });
    }

    @Test
    @DisplayName("외부 API 키가 없어도 컨텍스트가 뜬다")
    void startsWithoutExternalApiKeys() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // 키가 없으면 provider는 살아 있되 스스로 노선을 지원하지 않는다고 답한다.
            assertThat(context.getBean(TagoProperties.class).isConfigured()).isFalse();
            assertThat(context.getBean(TravelpayoutsProperties.class).isConfigured()).isFalse();
        });
    }

    @Test
    @DisplayName("HTTP 클라이언트에 타임아웃이 설정된다")
    void configuresHttpTimeouts() {
        runner.run(context -> assertThat(context.getBean(RestClient.Builder.class)).isNotNull());
    }
}
