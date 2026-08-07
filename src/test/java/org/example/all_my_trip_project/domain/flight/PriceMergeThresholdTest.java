package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchResult;
import org.example.all_my_trip_project.domain.flight.provider.CompositeFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.FlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.provider.TravelpayoutsProperties;
import org.example.all_my_trip_project.domain.flight.service.FlightRecommendationScorer;
import org.example.all_my_trip_project.domain.flight.service.FlightScheduleAnalyzer;
import org.example.all_my_trip_project.domain.flight.type.Badge;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Travelpayouts는 캐시 기반이라 특정 날짜에 노선당 1~2편만 준다.
 * 실측으로 김포→제주는 118편 중 1편만 매칭됐다.
 *
 * <p>공시운임이 실판매가의 약 2배라, 그 한 편만 실판매가로 바꾸면 실제로 싸서가 아니라
 * 캐시에 우연히 들어있어서 최저가와 추천 1위를 가져간다. 그걸 막는 장치를 검증한다.
 */
class PriceMergeThresholdTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    private FlightOffer published(String flightNumber, int hour, long perAdult) {
        return offer(flightNumber, hour, perAdult, PriceSource.PUBLISHED, "mock", null);
    }

    private FlightOffer quote(String flightNumber, int hour, long perAdult) {
        return offer(flightNumber, hour, perAdult, PriceSource.MARKET, "travelpayouts",
                "https://www.aviasales.com/search/x?marker=761521");
    }

    private FlightOffer offer(String flightNumber, int hour, long perAdult,
                              PriceSource source, String provider, String deeplink) {
        LocalDateTime departureAt = LocalDateTime.of(DATE, LocalTime.of(hour, 0));
        return new FlightOffer(
                provider + ":" + flightNumber, provider, flightNumber.substring(0, 2), "테스트항공",
                flightNumber, "GMP", "CJU", departureAt, departureAt.plusMinutes(70),
                Duration.ofMinutes(70), BigDecimal.valueOf(perAdult),
                BigDecimal.valueOf(perAdult * 2), "KRW", source, List.of(), deeplink);
    }

    /** 목록은 schedule provider가, 가격은 price provider가 준다. */
    private FlightSearchProvider stub(ProviderRole role, List<FlightOffer> offers) {
        return new FlightSearchProvider() {
            @Override public String name() { return role == ProviderRole.SCHEDULE ? "mock" : "travelpayouts"; }
            @Override public ProviderRole role() { return role; }
            @Override public boolean supports(FlightSearchQuery query) { return true; }
            @Override public List<FlightOffer> search(FlightSearchQuery query) { return offers; }
        };
    }

    private FlightSearchResult run(List<FlightOffer> schedule, List<FlightOffer> quotes, double threshold) {
        TravelpayoutsProperties properties = new TravelpayoutsProperties();
        properties.setPriceMergeThreshold(threshold);
        CompositeFlightSearchProvider composite = new CompositeFlightSearchProvider(
                List.of(stub(ProviderRole.SCHEDULE, schedule), stub(ProviderRole.PRICE, quotes)),
                new FlightRecommendationScorer(new FlightScheduleAnalyzer()),
                properties);
        return composite.search(new FlightSearchQuery("GMP", "CJU", DATE, 2, true, "KRW", null, null));
    }

    private List<FlightOffer> threePublished() {
        return List.of(published("KE121", 8, 61_900),
                published("OZ8901", 10, 61_900),
                published("RS907", 18, 61_900));
    }

    @Test
    @DisplayName("커버리지가 임계값에 못 미치면 공시운임을 유지한다")
    void keepsPublishedFaresBelowThreshold() {
        // 3편 중 1편만 매칭(33%) — 임계값 50% 미달
        FlightSearchResult result = run(threePublished(), List.of(quote("RS907", 18, 33_703)), 0.5);

        assertThat(result.offers()).allMatch(o -> o.priceSource() == PriceSource.PUBLISHED);
        assertThat(result.matchedPriceCount()).isZero();
    }

    @Test
    @DisplayName("커버리지가 낮아도 매칭된 편의 딥링크는 가져다 쓴다")
    void stillAdoptsDeeplinkBelowThreshold() {
        FlightSearchResult result = run(threePublished(), List.of(quote("RS907", 18, 33_703)), 0.5);

        FlightOffer matched = result.offers().stream()
                .filter(o -> o.flightNumber().equals("RS907")).findFirst().orElseThrow();
        assertThat(matched.deeplinkUrl()).contains("aviasales.com").contains("marker=");
    }

    @Test
    @DisplayName("커버리지가 낮으면 캐시에 우연히 든 편이 최저가를 가져가지 않는다")
    void cachedOfferDoesNotStealLowestPriceBadge() {
        FlightSearchResult result = run(threePublished(), List.of(quote("RS907", 18, 33_703)), 0.5);

        FlightOffer matched = result.offers().stream()
                .filter(o -> o.flightNumber().equals("RS907")).findFirst().orElseThrow();
        // 운임이 전부 같으므로 특정 편만 최저가로 튀어오르면 안 된다.
        assertThat(matched.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(123_800));
        assertThat(result.offers().stream().filter(o -> o.badges().contains(Badge.LOWEST_PRICE))).hasSize(1);
    }

    @Test
    @DisplayName("커버리지가 임계값을 넘으면 실판매가로 덮어쓴다")
    void mergesPricesAboveThreshold() {
        FlightSearchResult result = run(threePublished(),
                List.of(quote("RS907", 18, 33_703), quote("KE121", 8, 40_000)), 0.5);

        assertThat(result.matchedPriceCount()).isEqualTo(2);
        FlightOffer merged = result.offers().stream()
                .filter(o -> o.flightNumber().equals("RS907")).findFirst().orElseThrow();
        assertThat(merged.priceSource()).isEqualTo(PriceSource.MARKET);
        assertThat(merged.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(67_406));
        assertThat(merged.badges()).contains(Badge.LOWEST_PRICE);
    }

    @Test
    @DisplayName("가격 provider가 예외를 던져도 목록은 살아남는다")
    void survivesPriceProviderFailure() {
        FlightSearchProvider failing = new FlightSearchProvider() {
            @Override public String name() { return "travelpayouts"; }
            @Override public ProviderRole role() { return ProviderRole.PRICE; }
            @Override public boolean supports(FlightSearchQuery query) { return true; }
            @Override public List<FlightOffer> search(FlightSearchQuery query) {
                throw new IllegalStateException("timeout");
            }
        };
        TravelpayoutsProperties properties = new TravelpayoutsProperties();
        CompositeFlightSearchProvider composite = new CompositeFlightSearchProvider(
                List.of(stub(ProviderRole.SCHEDULE, threePublished()), failing),
                new FlightRecommendationScorer(new FlightScheduleAnalyzer()), properties);

        FlightSearchResult result = composite.search(
                new FlightSearchQuery("GMP", "CJU", DATE, 2, true, "KRW", null, null));

        assertThat(result.offers()).hasSize(3);
        assertThat(result.offers()).allMatch(o -> o.priceSource() == PriceSource.PUBLISHED);
    }
}
