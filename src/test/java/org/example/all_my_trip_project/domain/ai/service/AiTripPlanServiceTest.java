package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTripPlanServiceTest {
    private final AiTripPlanService aiTripPlanService = new AiTripPlanService();

    @Test
    void generatesDayByDayMockPlanFromTravelConditions() {
        AiTripPlanResponse response = aiTripPlanService.generate(new AiTripPlanRequest(
                "부산",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                "커플",
                "맛집, 카페",
                "균형있는",
                "대중교통",
                "유명 맛집",
                "호텔",
                new BigDecimal("1000000")
        ));

        assertThat(response.title()).isEqualTo("부산 3일 여행 초안");
        assertThat(response.generatedBy()).isEqualTo("SERVER_MOCK");
        assertThat(response.days()).hasSize(3);
        assertThat(response.days().getFirst().places())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "추천 명소", "숙소");
        assertThat(response.days().getLast().places())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "추천 명소");
        assertThat(response.days().getLast().items().getLast().title()).isEqualTo("귀가");
        assertThat(response.recommendedPlaces())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "추천 명소", "숙소");
        assertThat(response.days().getFirst().items().getFirst().title())
                .contains("부산", "브런치");
        assertThat(response.days().getFirst().items().get(1).description())
                .contains("2명", "1,000,000원");
    }

    @Test
    void rejectsTripLongerThanThirtyDays() {
        AiTripPlanRequest request = new AiTripPlanRequest(
                "부산",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                1,
                "혼자",
                "관광",
                "여유로운",
                "대중교통",
                "로컬 맛집",
                "호텔",
                new BigDecimal("500000")
        );

        assertThatThrownBy(() -> aiTripPlanService.generate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행 기간은 최대 30일까지 선택할 수 있습니다.");
    }
}
