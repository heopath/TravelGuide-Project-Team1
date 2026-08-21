package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.provider.CompositeAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.LiteApiSandboxPriceProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.LiteApiSandboxProperties;
import org.example.all_my_trip_project.domain.accommodation.provider.MockAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationDeeplinkProperties;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationRecommendationScorer;
import org.example.all_my_trip_project.domain.accommodation.service.SearchAccommodationDeeplinkBuilder;
import org.example.all_my_trip_project.domain.flight.config.FlightHttpClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 숙박 도메인 빈이 실제로 배선되는지 확인한다.
 *
 * <p><b>이 테스트가 없어서 기동 실패를 빌드에서 놓쳤다.</b>
 * {@code TourApiAccommodationSearchProvider}가 Jackson 2의 {@code ObjectMapper}를
 * 주입받도록 되어 있었는데 Spring Boot 4의 기본은 Jackson 3라 해당 빈이 없다.
 * 단위 테스트는 생성자를 직접 호출해 통과했고 컨텍스트를 띄우는 테스트가 없어,
 * {@code bootRun} 전까지 드러나지 않았다.
 *
 * <p>항공의 {@code FlightBeanWiringTest}와 같은 이유·같은 방식이다.
 * DB도 Redis도 필요 없는 슬라이스라 빌드에서 매번 돈다.
 */
class AccommodationBeanWiringTest {

    /*
     * RestClient.Builder는 항공의 FlightHttpClientConfig가 제공한다.
     * 숙박이 별도 HTTP 설정을 두지 않고 그 빈을 함께 쓰고 있어 여기서도 같은 설정을 올린다.
     * 숙박 전용 설정이 생기면 이 줄을 바꾼다.
     */
    private ApplicationContextRunner runner(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);

        return new ApplicationContextRunner()
                .withUserConfiguration(FlightHttpClientConfig.class)
                .withBean(TourApiProperties.class)
                .withBean(LiteApiSandboxProperties.class)
                .withBean(AccommodationRecommendationScorer.class)
                /* provider가 offer마다 예약 사이트 주소를 만들 때 쓴다. 없으면 두 provider가 다 못 뜬다. */
                .withBean(AccommodationDeeplinkProperties.class)
                .withBean(SearchAccommodationDeeplinkBuilder.class)
                .withBean(TourApiAccommodationSearchProvider.class)
                .withBean(LiteApiSandboxPriceProvider.class,
                        () -> new LiteApiSandboxPriceProvider(
                                new LiteApiSandboxProperties(),
                                RestClient.builder(),
                                environment))
                .withBean(CompositeAccommodationSearchProvider.class);
    }

    @Test
    @DisplayName("숙박 provider가 전부 배선된다")
    void wiresAllProviders() {
        runner().withBean(MockAccommodationSearchProvider.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasSingleBean(TourApiAccommodationSearchProvider.class);
            assertThat(context).hasSingleBean(LiteApiSandboxPriceProvider.class);
            assertThat(context).hasSingleBean(MockAccommodationSearchProvider.class);
            assertThat(context).hasSingleBean(CompositeAccommodationSearchProvider.class);
        });
    }

    @Test
    @DisplayName("외부 API 키가 없어도 컨텍스트가 뜬다")
    void startsWithoutExternalApiKeys() {
        runner().withBean(MockAccommodationSearchProvider.class).run(context -> {
            assertThat(context).hasNotFailed();
            // 키가 없으면 provider는 살아 있되 스스로 지역을 지원하지 않는다고 답하고,
            // composite가 다음 LISTING provider로 넘어간다.
            assertThat(context.getBean(TourApiProperties.class).isConfigured()).isFalse();
        });
    }

    /**
     * Mock provider 없이도 composite가 뜨는지 본다.
     *
     * <p>운영에서는 Mock이 등록되지 않아 LISTING provider가 TourAPI 하나뿐이다.
     * 그 상태에서 컨텍스트가 깨지면 숙소 탭이 아니라 애플리케이션 전체가 못 뜬다.
     */
    @Test
    @DisplayName("Mock 없이(운영 구성) 컨텍스트가 뜬다")
    void startsWithoutMockProvider() {
        runner("prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CompositeAccommodationSearchProvider.class);
            assertThat(context).doesNotHaveBean(MockAccommodationSearchProvider.class);
        });
    }

    /**
     * Mock이 운영 프로필에서 빠지는지 어노테이션으로 직접 확인한다.
     *
     * <p>위 테스트만으로는 부족하다. {@code ApplicationContextRunner.withBean}은 프로필을
     * 무시하고 빈을 직접 등록하므로, Mock을 빼고 돌린 것이지 {@code @Profile}이 동작해서
     * 빠진 것이 아니다. 누군가 이 어노테이션을 지워도 위 테스트는 그대로 통과한다.
     *
     * <p>이 가드가 없으면 TourAPI 장애·키 미설정·검색 결과 없음이 전부 Mock 폴백으로
     * 이어지고, 출처 없는 Mock 요금이 운영 화면에 노출될 수 있다. Sandbox 실습 요금은
     * 별도 provider가 공급하며 화면에서 실습 요금임을 밝힌다.
     */
    @Test
    @DisplayName("Mock provider에 운영 제외 프로필이 걸려 있다")
    void mockProviderIsExcludedFromProductionProfile() {
        Profile profile = MockAccommodationSearchProvider.class.getAnnotation(Profile.class);

        assertThat(profile)
                .as("MockAccommodationSearchProvider에 @Profile이 없으면 운영에서도 등록된다")
                .isNotNull();
        assertThat(profile.value()).contains("!prod");
    }
}
