package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationPriceResult;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.provider.LiteApiSandboxPriceProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.LiteApiSandboxProperties;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.example.all_my_trip_project.domain.accommodation.provider.AccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.CompositeAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationRecommendationScorer;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationSearchService;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiteApiSandboxPriceProviderTest {

    private static final String BASE_URL = "https://lite.test/v3.0";

    private record Fixture(LiteApiSandboxPriceProvider provider,
                           MockRestServiceServer server,
                           LiteApiSandboxProperties properties,
                           MockEnvironment environment) {}

    private AccommodationSearchQuery query() {
        return new AccommodationSearchQuery("부산 해운대",
                LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 13), 2, 1, "USD");
    }

    private AccommodationOffer tourOffer() {
        return new AccommodationOffer(
                "tourapi:12345", "tourapi", "해운대 테스트 호텔", AccommodationType.HOTEL,
                "부산광역시 해운대구", "부산광역시 해운대구 해운대로 1",
                null, null, null, null, "USD", AccommodationPriceSource.UNAVAILABLE,
                List.of(), false, false, "https://image.test/hotel.jpg",
                35.1587, 129.1604, null, 0.0
        );
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiteApiSandboxProperties properties = new LiteApiSandboxProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("sand_test-key");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return new Fixture(new LiteApiSandboxPriceProvider(
                properties, builder, environment), server, properties, environment);
    }

    @Test
    @DisplayName("서버용 Sandbox Key로 rates를 호출해 총액·통화·취소 조건을 붙인다")
    void mapsSandboxRate() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/hotels/rates?rm=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "sand_test-key"))
                .andExpect(jsonPath("$.checkin").value("2027-02-10"))
                .andExpect(jsonPath("$.checkout").value("2027-02-13"))
                .andExpect(jsonPath("$.occupancies[0].adults").value(2))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.roomMapping").value(true))
                .andExpect(jsonPath("$.maxRatesPerHotel").value(1))
                .andRespond(withSuccess(sandboxResponse(true), MediaType.APPLICATION_JSON));

        AccommodationPriceResult result = fixture.provider().apply(List.of(tourOffer()), query());

        assertThat(result.matchedCount()).isEqualTo(1);
        AccommodationOffer priced = result.offers().get(0);
        assertThat(priced.priceSource()).isEqualTo(AccommodationPriceSource.SANDBOX);
        assertThat(priced.totalPrice()).isEqualByComparingTo(new BigDecimal("575.24"));
        assertThat(priced.nightlyPrice()).isEqualByComparingTo(new BigDecimal("191.75"));
        assertThat(priced.currency()).isEqualTo("USD");
        assertThat(priced.freeCancellation()).isTrue();
        assertThat(priced.breakfastIncluded()).isTrue();
        fixture.server().verify();
    }

    @Test
    @DisplayName("sandbox=false 응답의 가격은 화면 데이터에 섞지 않는다")
    void rejectsNonSandboxResponse() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/hotels/rates?rm=true"))
                .andRespond(withSuccess(sandboxResponse(false), MediaType.APPLICATION_JSON));

        AccommodationPriceResult result = fixture.provider().apply(List.of(tourOffer()), query());

        assertThat(result.matchedCount()).isZero();
        assertThat(result.offers().get(0).priceSource()).isEqualTo(AccommodationPriceSource.UNAVAILABLE);
        fixture.server().verify();
    }

    @Test
    @DisplayName("공개키나 빈 키는 rates 호출용 키로 인정하지 않는다")
    void rejectsMissingOrPublicKey() {
        Fixture fixture = fixture();
        fixture.properties().setApiKey("pk_test_public-key");
        assertThat(fixture.provider().supports(query(), List.of(tourOffer()))).isFalse();

        fixture.properties().setApiKey("");
        assertThat(fixture.provider().supports(query(), List.of(tourOffer()))).isFalse();
    }

    @Test
    @DisplayName("prod 프로필에서는 Sandbox Key가 있어도 가격 provider가 비활성화된다")
    void disablesSandboxInProduction() {
        Fixture fixture = fixture();
        fixture.environment().setActiveProfiles("prod");

        assertThat(fixture.provider().supports(query(), List.of(tourOffer()))).isFalse();
    }

    @Test
    @DisplayName("방어 로직이 prod 응답에 섞인 Sandbox 가격도 거부한다")
    void rejectsSandboxPriceFromProductionResponse() {
        AccommodationOffer sandboxOffer = tourOffer().withRate(
                new BigDecimal("575.24"), "USD", AccommodationPriceSource.SANDBOX,
                3, 1, true, false);
        AccommodationSearchProvider listing = new AccommodationSearchProvider() {
            @Override public String name() { return "unexpected-sandbox-listing"; }
            @Override public AccommodationProviderRole role() { return AccommodationProviderRole.LISTING; }
            @Override public boolean supports(AccommodationSearchQuery query) { return true; }
            @Override public List<AccommodationOffer> search(AccommodationSearchQuery query) {
                return List.of(sandboxOffer);
            }
        };
        CompositeAccommodationSearchProvider composite = new CompositeAccommodationSearchProvider(
                List.of(listing), new AccommodationRecommendationScorer());
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        AccommodationSearchService service = new AccommodationSearchService(composite, production);

        assertThatThrownBy(() -> service.search(query()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("실습용 숙소 요금");
    }

    @Test
    @DisplayName("Spring에 Jackson 2 ObjectMapper Bean이 없어도 숙소 provider가 생성된다")
    void startsProvidersWithoutObjectMapperBean() {
        new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(TourApiProperties.class)
                .withBean(LiteApiSandboxProperties.class)
                .withBean(TourApiAccommodationSearchProvider.class)
                .withBean(LiteApiSandboxPriceProvider.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TourApiAccommodationSearchProvider.class);
                    assertThat(context).hasSingleBean(LiteApiSandboxPriceProvider.class);
                });
    }

    private String sandboxResponse(boolean sandbox) {
        return """
                {
                  "sandbox": %s,
                  "hotels": [{
                    "id": "lp1897",
                    "name": "해운대 테스트 호텔",
                    "address": "부산광역시 해운대구 해운대로 1",
                    "latitude": 35.15871,
                    "longitude": 129.16039
                  }],
                  "data": [{
                    "hotelId": "lp1897",
                    "roomTypes": [{
                      "offerId": "sandbox-offer-id-not-stored",
                      "rates": [{
                        "name": "Double Room with Breakfast",
                        "boardType": "BB",
                        "boardName": "Bed and Breakfast",
                        "retailRate": {"total": [{"amount": 575.24, "currency": "USD"}]},
                        "cancellationPolicies": {"refundableTag": "RFN"}
                      }]
                    }]
                  }]
                }
                """.formatted(sandbox);
    }
}
