package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.service.FlightRecommendationScorer;
import org.example.all_my_trip_project.domain.flight.service.FlightScheduleAnalyzer;
import org.example.all_my_trip_project.domain.flight.type.Badge;
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

class FlightRecommendationScorerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    private final FlightRecommendationScorer scorer =
            new FlightRecommendationScorer(new FlightScheduleAnalyzer());

    private FlightOffer offer(String flightNumber, int hour, int minute, long totalPrice) {
        LocalDateTime departureAt = LocalDateTime.of(DATE, LocalTime.of(hour, minute));
        LocalDateTime arrivalAt = departureAt.plusMinutes(70);
        return new FlightOffer(
                "mock:" + flightNumber, "mock", flightNumber.substring(0, 2), "테스트항공",
                flightNumber, "ICN", "CJU", departureAt, arrivalAt,
                Duration.between(departureAt, arrivalAt),
                BigDecimal.valueOf(totalPrice / 2), BigDecimal.valueOf(totalPrice),
                "KRW", PriceSource.PUBLISHED, List.of(), "https://example.test");
    }

    private FlightSearchQuery query(LocalDateTime firstPlanStartAt, LocalDateTime lastPlanEndAt) {
        return new FlightSearchQuery("ICN", "CJU", DATE, 2, true, "KRW",
                firstPlanStartAt, lastPlanEndAt);
    }

    @Test
    @DisplayName("일정 정보가 없으면 페널티가 0이라 가격만으로 순위가 갈린다")
    void ranksByPriceWhenNoItinerary() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("KE121", 8, 10, 178_000),
                        offer("7C101", 10, 30, 152_000),
                        offer("LJ301", 14, 0, 164_000)),
                query(null, null));

        assertThat(ranked).extracting(FlightOffer::flightNumber)
                .containsExactly("7C101", "LJ301", "KE121");
    }

    @Test
    @DisplayName("첫 일정보다 늦게 도착하면 배지가 붙고 순위가 밀린다")
    void penalisesLateArrivalForFirstPlan() {
        // 후보가 2개뿐이면 가격 점수가 0 아니면 1이라 60% 가중치가 항상 이긴다.
        // 일정 페널티가 순위를 뒤집으려면 중간 가격대가 있어야 한다.
        // 7C101은 최저가지만 1일차 첫 활동(12:00)보다 늦게 도착해 -0.5를 맞는다.
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("KE121", 8, 10, 160_000),
                        offer("7C101", 14, 30, 152_000),
                        offer("OZ102", 9, 0, 200_000)),
                query(LocalDateTime.of(DATE, LocalTime.NOON), null));

        assertThat(ranked.get(0).flightNumber()).isEqualTo("KE121");
        assertThat(ranked.get(0).badges()).doesNotContain(Badge.LATE_FOR_FIRST_PLAN);

        FlightOffer late = ranked.stream()
                .filter(o -> o.flightNumber().equals("7C101")).findFirst().orElseThrow();
        assertThat(late.badges()).contains(Badge.LATE_FOR_FIRST_PLAN);
    }

    @Test
    @DisplayName("마지막 일정이 끝나기 전에 출발하면 배지가 붙는다")
    void flagsMissedLastPlan() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("TW716", 15, 20, 136_000),
                        offer("KE1284", 18, 40, 188_000)),
                query(null, LocalDateTime.of(DATE, LocalTime.of(17, 0))));

        FlightOffer early = ranked.stream()
                .filter(o -> o.flightNumber().equals("TW716")).findFirst().orElseThrow();
        assertThat(early.badges()).contains(Badge.MISSES_LAST_PLAN);
    }

    @Test
    @DisplayName("06:00 이전 출발은 이른 출발 배지가 붙는다")
    void flagsEarlyDeparture() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("7C101", 5, 40, 120_000),
                        offer("KE121", 8, 10, 178_000)),
                query(null, null));

        FlightOffer early = ranked.stream()
                .filter(o -> o.flightNumber().equals("7C101")).findFirst().orElseThrow();
        assertThat(early.badges()).contains(Badge.EARLY_DEPARTURE);
    }

    @Test
    @DisplayName("AI 추천은 1위에, 최저가는 가장 싼 편에 각각 하나씩 붙는다")
    void attachesOneRibbonOfEachKind() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("KE121", 8, 10, 178_000),
                        offer("7C101", 14, 30, 152_000),
                        offer("LJ301", 11, 0, 164_000)),
                query(LocalDateTime.of(DATE, LocalTime.NOON), null));

        assertThat(ranked.get(0).badges()).contains(Badge.AI_PICK);
        assertThat(ranked.stream().filter(o -> o.badges().contains(Badge.AI_PICK))).hasSize(1);

        FlightOffer cheapest = ranked.stream()
                .filter(o -> o.badges().contains(Badge.LOWEST_PRICE)).findFirst().orElseThrow();
        assertThat(cheapest.flightNumber()).isEqualTo("7C101");
        assertThat(ranked.stream().filter(o -> o.badges().contains(Badge.LOWEST_PRICE))).hasSize(1);
    }

    @Test
    @DisplayName("운임이 모두 같으면 가격으로 감점하지 않고 일정 적합도가 순위를 정한다")
    void scheduleDecidesWhenPricesAreEqual() {
        List<FlightOffer> ranked = scorer.rank(
                List.of(offer("7C101", 14, 30, 160_000),
                        offer("KE121", 8, 10, 160_000)),
                query(LocalDateTime.of(DATE, LocalTime.NOON), null));

        assertThat(ranked.get(0).flightNumber()).isEqualTo("KE121");
    }

    @Test
    @DisplayName("결과가 없어도 예외 없이 빈 목록을 돌려준다")
    void handlesEmptyResult() {
        assertThat(scorer.rank(List.of(), query(null, null))).isEmpty();
    }

    @Test
    @DisplayName("priceSource가 없는 offer는 만들 수 없다")
    void rejectsNullPriceSource() {
        LocalDateTime departureAt = LocalDateTime.of(DATE, LocalTime.of(8, 0));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new FlightOffer(
                "mock:x", "mock", "KE", "테스트항공", "KE1", "ICN", "CJU",
                departureAt, departureAt.plusMinutes(70), Duration.ofMinutes(70),
                BigDecimal.ONE, BigDecimal.TEN, "KRW", null, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
