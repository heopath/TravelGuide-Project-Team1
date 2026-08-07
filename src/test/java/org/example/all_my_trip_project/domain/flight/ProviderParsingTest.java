package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.provider.TagoFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.TagoProperties;
import org.example.all_my_trip_project.domain.flight.provider.TravelpayoutsPriceProvider;
import org.example.all_my_trip_project.domain.flight.provider.TravelpayoutsProperties;
import org.example.all_my_trip_project.domain.flight.service.DeeplinkBuilder;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 API 응답을 그대로 박제해서 파싱을 검증한다.
 *
 * <p>두 provider 모두 Jackson 바인딩 대신 문자열을 직접 자른다.
 * data.go.kr이 결과 0건일 때 {@code items}를 객체가 아닌 빈 문자열로 주는 경우가 있어
 * 타입 바인딩이 통째로 깨지기 때문인데, 그만큼 실제 페이로드로 고정해두지 않으면
 * 응답 모양이 조금만 달라져도 조용히 빈 목록이 된다.
 *
 * <p>픽스처는 2026-08-07에 실제 호출로 받은 응답이다. 인증키는 들어 있지 않다.
 */
class ProviderParsingTest {

    private static final String TAGO_BASE = "https://tago.test/DmstcFlightNvgInfo";
    private static final String TP_BASE = "https://tp.test/aviasales/v3";

    /** 딥링크 조합은 별도 관심사라 여기서는 고정값으로 둔다. */
    private final DeeplinkBuilder deeplinkBuilder = (offer, query) -> "https://carrier.test/book";

    private String fixture(String name) throws IOException {
        return new ClassPathResource("fixtures/flight/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private FlightSearchQuery query(String origin, String destination, LocalDate date) {
        return new FlightSearchQuery(origin, destination, date, 2, true, "KRW", null, null);
    }

    // ────────── TAGO ──────────

    private record TagoFixture(TagoFlightSearchProvider provider, MockRestServiceServer server) {}

    private TagoFixture tago() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TagoProperties properties = new TagoProperties();
        properties.setBaseUrl(TAGO_BASE);
        properties.setServiceKey("test%2Fkey%3D%3D");
        return new TagoFixture(
                new TagoFlightSearchProvider(properties, deeplinkBuilder, builder), server);
    }

    @Test
    @DisplayName("TAGO 실응답을 FlightOffer로 매핑한다")
    void mapsRealTagoResponse() throws IOException {
        TagoFixture f = tago();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("depAirportId=NAARKSS")))
                .andRespond(withSuccess(fixture("tago-gmp-cju.json"), MediaType.APPLICATION_JSON));

        List<FlightOffer> offers = f.provider()
                .search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)));

        assertThat(offers).isNotEmpty();
        FlightOffer first = offers.get(0);
        assertThat(first.flightNumber()).isEqualTo("OZ8901");
        assertThat(first.carrierCode()).isEqualTo("OZ");
        assertThat(first.carrierName()).isEqualTo("아시아나항공");
        assertThat(first.origin()).isEqualTo("GMP");
        assertThat(first.destination()).isEqualTo("CJU");
        assertThat(first.departureAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 6, 30));
        assertThat(first.arrivalAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 7, 45));
        assertThat(first.pricePerAdult()).isEqualByComparingTo(BigDecimal.valueOf(61_900));
        // 성인 2명 총액
        assertThat(first.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(123_800));
        assertThat(first.priceSource()).isEqualTo(PriceSource.PUBLISHED);
        assertThat(first.offerId()).isEqualTo("tago:OZ8901-20260901");
    }

    @Test
    @DisplayName("economyCharge=0인 실제 항공편은 운임 미제공으로 매핑된다")
    void mapsZeroFareAsUnavailable() throws IOException {
        TagoFixture f = tago();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("depAirportId=NAARKSI")))
                .andRespond(withSuccess(fixture("tago-icn-cju-zero-fare.json"), MediaType.APPLICATION_JSON));

        List<FlightOffer> offers = f.provider()
                .search(query("ICN", "CJU", LocalDate.of(2026, 8, 10)));

        assertThat(offers).hasSize(1);
        FlightOffer only = offers.get(0);
        assertThat(only.flightNumber()).isEqualTo("7C167");
        // 0원이 아니라 "값이 없음"이어야 한다.
        assertThat(only.totalPrice()).isNull();
        assertThat(only.priceSource()).isEqualTo(PriceSource.UNAVAILABLE);
        assertThat(only.hasPrice()).isFalse();
    }

    @Test
    @DisplayName("결과 0건이어도 빈 목록을 돌려준다")
    void handlesEmptyTagoResult() {
        TagoFixture f = tago();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("depAirportId=")))
                .andRespond(withSuccess(
                        "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},"
                                + "\"body\":{\"items\":{\"item\":[]},\"numOfRows\":10,\"pageNo\":1,\"totalCount\":0}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(f.provider().search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)))).isEmpty();
    }

    @Test
    @DisplayName("XML 오류 응답이 와도 예외 없이 빈 목록을 돌려준다")
    void handlesXmlErrorResponse() {
        TagoFixture f = tago();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("depAirportId=")))
                .andRespond(withSuccess(
                        "<OpenAPI_ServiceResponse><cmmMsgHeader>"
                                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                                + "</cmmMsgHeader></OpenAPI_ServiceResponse>",
                        MediaType.APPLICATION_XML));

        assertThat(f.provider().search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)))).isEmpty();
    }

    @Test
    @DisplayName("서비스키가 없으면 provider가 스스로 비활성화된다")
    void disablesItselfWithoutServiceKey() {
        TagoProperties properties = new TagoProperties();
        TagoFlightSearchProvider provider =
                new TagoFlightSearchProvider(properties, deeplinkBuilder, RestClient.builder());

        assertThat(provider.supports(query("GMP", "CJU", LocalDate.of(2026, 9, 1)))).isFalse();
    }

    @Test
    @DisplayName("국내 공항이 아니면 지원하지 않는다")
    void doesNotSupportInternationalRoutes() {
        TagoProperties properties = new TagoProperties();
        properties.setServiceKey("test");
        TagoFlightSearchProvider provider =
                new TagoFlightSearchProvider(properties, deeplinkBuilder, RestClient.builder());

        assertThat(provider.supports(query("ICN", "NRT", LocalDate.of(2026, 9, 1)))).isFalse();
    }

    // ────────── Travelpayouts ──────────

    private record TpFixture(TravelpayoutsPriceProvider provider, MockRestServiceServer server) {}

    private TpFixture travelpayouts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TravelpayoutsProperties properties = new TravelpayoutsProperties();
        properties.setBaseUrl(TP_BASE);
        properties.setToken("test-token");
        properties.setMarker("761521");
        return new TpFixture(new TravelpayoutsPriceProvider(properties, builder), server);
    }

    @Test
    @DisplayName("Travelpayouts 실응답을 매칭 가능한 형태로 매핑한다")
    void mapsRealTravelpayoutsResponse() throws IOException {
        TpFixture f = travelpayouts();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("origin=GMP")))
                .andRespond(withSuccess(fixture("travelpayouts-gmp-cju.json"), MediaType.APPLICATION_JSON));

        List<FlightOffer> quotes = f.provider()
                .search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)));

        assertThat(quotes).hasSize(1);
        FlightOffer quote = quotes.get(0);
        // TAGO의 vihicleId와 같은 형태여야 매칭 키가 맞는다.
        assertThat(quote.flightNumber()).isEqualTo("RS907");
        assertThat(quote.carrierCode()).isEqualTo("RS");
        assertThat(quote.priceSource()).isEqualTo(PriceSource.MARKET);
        assertThat(quote.pricePerAdult()).isEqualByComparingTo(BigDecimal.valueOf(33_703));
        assertThat(quote.matchKey()).isEqualTo("RSRS907@2026-09-01");
        assertThat(quote.deeplinkUrl())
                .startsWith("https://www.aviasales.com/search/")
                .contains("marker=761521");
    }

    @Test
    @DisplayName("marker가 없으면 추적 안 되는 딥링크를 만들지 않는다")
    void skipsDeeplinkWithoutMarker() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TravelpayoutsProperties properties = new TravelpayoutsProperties();
        properties.setBaseUrl(TP_BASE);
        properties.setToken("test-token");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("origin=GMP")))
                .andRespond(withSuccess(fixture("travelpayouts-gmp-cju.json"), MediaType.APPLICATION_JSON));

        List<FlightOffer> quotes = new TravelpayoutsPriceProvider(properties, builder)
                .search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)));

        assertThat(quotes.get(0).deeplinkUrl()).isNull();
    }

    @Test
    @DisplayName("결과가 비어도 예외 없이 빈 목록을 돌려준다")
    void handlesEmptyTravelpayoutsResult() {
        TpFixture f = travelpayouts();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("origin=ICN")))
                .andRespond(withSuccess("{\"data\":[],\"currency\":\"krw\",\"success\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(f.provider().search(query("ICN", "CJU", LocalDate.of(2026, 9, 1)))).isEmpty();
    }

    @Test
    @DisplayName("Travelpayouts가 500을 내면 예외가 위로 전달되어 Composite가 공시운임을 유지한다")
    void propagatesServerErrorToComposite() {
        TpFixture f = travelpayouts();
        f.server().expect(requestTo(org.hamcrest.Matchers.containsString("origin=GMP")))
                .andRespond(withServerError());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> f.provider().search(query("GMP", "CJU", LocalDate.of(2026, 9, 1)))))
                .isNotNull();
    }
}
