package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.service.FlightRecommendationScorer;
import org.example.all_my_trip_project.domain.flight.service.FlightScheduleAnalyzer;
import org.example.all_my_trip_project.domain.flight.type.Badge;
import org.example.all_my_trip_project.domain.flight.type.DomesticAirport;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * TAGO는 운임을 모르는 편에 economyCharge=0을 준다 (예: 인천→제주 7C167).
 * 0원 항공편이 아니라 정보가 없는 것이므로, 어디서도 싼 편으로 취급되면 안 된다.
 */
class UnavailableFareTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    private final FlightRecommendationScorer scorer =
            new FlightRecommendationScorer(new FlightScheduleAnalyzer());

    private FlightOffer offer(String flightNumber, int hour, Long totalPrice) {
        LocalDateTime departureAt = LocalDateTime.of(DATE, LocalTime.of(hour, 0));
        LocalDateTime arrivalAt = departureAt.plusMinutes(70);
        boolean priced = totalPrice != null;
        return new FlightOffer(
                "tago:" + flightNumber, "tago", flightNumber.substring(0, 2), "테스트항공",
                flightNumber, "GMP", "CJU", departureAt, arrivalAt,
                Duration.between(departureAt, arrivalAt),
                priced ? BigDecimal.valueOf(totalPrice / 2) : null,
                priced ? BigDecimal.valueOf(totalPrice) : null,
                "KRW", priced ? PriceSource.PUBLISHED : PriceSource.UNAVAILABLE,
                List.of(), null);
    }

    private FlightSearchQuery query() {
        return new FlightSearchQuery("GMP", "CJU", DATE, 2, true, "KRW", null, null);
    }

    @Test
    @DisplayName("운임 미제공 항공편은 최저가 배지를 받지 못한다")
    void neverGetsLowestPriceBadge() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("7C167", 18, null), offer("KE121", 8, 160_000L)), query());

        FlightOffer unknown = ranked.stream()
                .filter(o -> o.flightNumber().equals("7C167")).findFirst().orElseThrow();
        assertThat(unknown.badges()).doesNotContain(Badge.LOWEST_PRICE);

        FlightOffer priced = ranked.stream()
                .filter(o -> o.flightNumber().equals("KE121")).findFirst().orElseThrow();
        assertThat(priced.badges()).contains(Badge.LOWEST_PRICE);
    }

    @Test
    @DisplayName("운임 미제공 항공편은 목록에 남되 뒤로 밀린다")
    void staysInListButRanksLast() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("7C167", 18, null),
                        offer("KE121", 8, 160_000L),
                        offer("OZ8901", 9, 200_000L)), query());

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(2).flightNumber()).isEqualTo("7C167");
    }

    @Test
    @DisplayName("운임이 전부 미제공이어도 예외 없이 정렬된다")
    void handlesAllUnavailable() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("7C167", 18, null), offer("KE121", 8, null)), query());

        assertThat(ranked).hasSize(2);
        assertThat(ranked).noneMatch(o -> o.badges().contains(Badge.LOWEST_PRICE));
        assertThat(ranked.get(0).badges()).contains(Badge.AI_PICK);
    }

    @Test
    @DisplayName("가격이 없는데 출처가 UNAVAILABLE이 아니면 만들 수 없다")
    void rejectsPricelessOfferWithPricedSource() {
        LocalDateTime at = LocalDateTime.of(DATE, LocalTime.of(8, 0));
        assertThat(catchThrowable(() -> new FlightOffer(
                "tago:x", "tago", "KE", "테스트항공", "KE1", "GMP", "CJU",
                at, at.plusMinutes(70), Duration.ofMinutes(70),
                null, null, "KRW", PriceSource.PUBLISHED, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IATA ↔ TAGO 공항 코드가 양방향으로 매핑된다")
    void mapsAirportCodesBothWays() {
        assertThat(DomesticAirport.ofIata("GMP").orElseThrow().getTagoCode()).isEqualTo("NAARKSS");
        assertThat(DomesticAirport.ofIata("cju").orElseThrow().getTagoCode()).isEqualTo("NAARKPC");
        assertThat(DomesticAirport.ofTagoCode("NAARKSI").orElseThrow().getIataCode()).isEqualTo("ICN");
        assertThat(DomesticAirport.ofIata("NRT")).isEmpty();
        assertThat(DomesticAirport.values()).hasSize(15);
    }
}
