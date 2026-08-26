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
                "호텔"
        ));

        assertThat(response.title()).isEqualTo("부산 3일 여행 초안");
        assertThat(response.generatedBy()).isEqualTo("SERVER_MOCK");
        assertThat(response.days()).hasSize(3);
        assertThat(response.days().getFirst().places())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "추천 명소", "숙소");
        assertThat(response.days().getLast().places())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "교통");
        assertThat(response.days().getLast().places().getLast().name()).contains("종합버스터미널");
        assertThat(response.days().getLast().items().getLast().title()).isEqualTo("귀가");
        assertThat(response.days())
                .allSatisfy(day -> assertThat(day.items()).hasSameSizeAs(day.places()));
        assertThat(response.recommendedPlaces())
                .extracting(place -> place.category())
                .containsExactly("추천 명소", "식사 장소", "추천 명소", "숙소");
        assertThat(response.days().getFirst().items().getFirst().title())
                .contains("부산", "브런치");
        assertThat(response.days().getFirst().items().get(1).description())
                .contains("2명");
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
                "호텔"
        );

        assertThatThrownBy(() -> aiTripPlanService.generate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("여행 기간은 최대 30일까지 선택할 수 있습니다.");
    }

    /*
     * 운영에서 Gemini가 JSON을 코드펜스로 감싸 보내 파싱이 500으로 실패했다(#234).
     * 프롬프트로 막아도 모델이 지키지 않을 때가 있어 응답을 받는 쪽에서 벗겨낸다.
     */
    @Test
    void stripsMarkdownCodeFenceFromGeminiResponse() {
        String json = "{\"title\":\"부산 여행\"}";

        assertThat(aiTripPlanService.stripCodeFence("```json\n" + json + "\n```")).isEqualTo(json);
        assertThat(aiTripPlanService.stripCodeFence("```\n" + json + "\n```")).isEqualTo(json);
        assertThat(aiTripPlanService.stripCodeFence("  ```json\n" + json + "\n```  ")).isEqualTo(json);
    }

    @Test
    void keepsPlainJsonUnchanged() {
        String json = "{\"title\":\"부산 여행\"}";

        assertThat(aiTripPlanService.stripCodeFence(json)).isEqualTo(json);
        assertThat(aiTripPlanService.stripCodeFence("  " + json + "  ")).isEqualTo(json);
    }

    @Test
    void keepsBackticksInsideJsonValues() {
        // 값 안의 백틱까지 건드리면 안 된다. 펜스로 시작할 때만 벗겨낸다.
        String json = "{\"title\":\"``코드`` 여행\"}";

        assertThat(aiTripPlanService.stripCodeFence(json)).isEqualTo(json);
    }
}
