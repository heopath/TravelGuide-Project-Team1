package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiAiModelClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final OpenAiAiModelClient client = new OpenAiAiModelClient(
            httpClient, new ObjectMapper(), "test-key", "gpt-5.6-terra", Duration.ofSeconds(25));

    @Test
    void generateMapsOpenAiResponsesJsonToCurrentGuideDto() throws Exception {
        stubResponse(200, """
                {"output":[{"type":"message","content":[{"type":"output_text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"해운대\\",\\"reason\\":\\"바다 산책에 좋아요\\"}]}]}"}]}]}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("부산 하루 일정 추천", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.answer()).isEqualTo("추천 일정");
        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("해운대");
        assertThat(response.sources()).contains("OpenAI");
    }

    @Test
    void generateRejectsOpenAiErrorResponse() throws Exception {
        stubResponse(429, "{\"error\":{\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\",\"message\":\"rate limit\"}}");

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("부산 하루 일정 추천", null),
                List.of(), new AiGuideContext(null, List.of())))
                .isInstanceOf(AiModelException.class)
                .hasMessage("OpenAI request failed. status=429, type=rate_limit_error, code=rate_limit_exceeded, message=rate limit");
    }

    @Test
    void generateRedactsApiKeyEchoedByOpenAiErrorResponse() throws Exception {
        stubResponse(400, "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"model_not_found\",\"message\":\"The requested model 'sk-proj-secret-value' does not exist.\"}}");

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("부산 하루 일정 추천", null),
                List.of(), new AiGuideContext(null, List.of())))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("[REDACTED]")
                .hasMessageNotContaining("sk-proj-secret-value");
    }

    @Test
    void generateUsesTextBlockAfterThinkingBlock() throws Exception {
        stubResponse(200, """
                {"message":{"content":[
                  {"type":"thinking","thinking":"reasoning"},
                  {"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"해운대\\",\\"reason\\":\\"바다 산책\\"}]}]}"}
                ]}}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("부산 여행", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.answer()).isEqualTo("추천 일정");
    }

    @Test
    void generateNormalizesZeroBasedDayAndBlankTitle() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":0,\\"title\\":\\"\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"해운대\\",\\"reason\\":\\"바다 산책\\"}]}]}"}]}}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("부산 여행", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days().getFirst().day()).isEqualTo(1);
        assertThat(response.days().getFirst().title()).isEqualTo("DAY 1 추천 일정");
    }

    @Test
    void retriesOnceWhenOpenAiReturnsAnInvalidScheduleItem() throws Exception {
        stubResponses(200,
                """
                        {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"\\",\\"name\\":\\"카페\\",\\"reason\\":\\"휴식\\"}]}]}"}]} }
                        """,
                """
                        {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"14:00\\",\\"name\\":\\"실제 카페\\",\\"reason\\":\\"휴식에 좋아요\\"}]}]}"}]}}
                        """);

        AiGuideResponse response = client.generate(new AiGuideRequest("카페 추천", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("14:00");
        org.mockito.Mockito.verify(httpClient, org.mockito.Mockito.times(2)).send(
                any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void retriesOnceWhenOpenAiReturnsInvalidFencedJson() throws Exception {
        stubResponses(200,
                """
                        {"message":{"content":[{"type":"text","text":"```json\\n{\\\"answer\\\":\\\"추천 일정\\\",\\\"days\\\":[{\\\"day\\\":1,\\\"title\\\":\\\"DAY 1\\\",\\\"items\\\":[{\\\"time\\\":\\\"14:00\\\",\\\"name\\\":\\\"카페\\\",\\\"reason\\\":\\\"휴식\\\"}]}]}"}]}}
                        """,
                """
                        {"message":{"content":[{"type":"text","text":"{\\\"answer\\\":\\\"추천 일정\\\",\\\"days\\\":[{\\\"day\\\":1,\\\"title\\\":\\\"DAY 1\\\",\\\"items\\\":[{\\\"time\\\":\\\"14:00\\\",\\\"name\\\":\\\"실제 카페\\\",\\\"reason\\\":\\\"휴식에 좋아요\\\"}]}]}"}]}}
                        """);

        AiGuideResponse response = client.generate(new AiGuideRequest("카페 추천", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("실제 카페");
        org.mockito.Mockito.verify(httpClient, org.mockito.Mockito.times(2)).send(
                any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void retriesOnceWhenOpenAiResponseContentIsBlank() throws Exception {
        stubResponses(200,
                """
                        {"message":{"content":[{"type":"text","text":""}]}}
                        """,
                """
                        {"message":{"content":[{"type":"text","text":"{\\\"answer\\\":\\\"추천 일정\\\",\\\"days\\\":[{\\\"day\\\":1,\\\"title\\\":\\\"DAY 1\\\",\\\"items\\\":[{\\\"time\\\":\\\"14:00\\\",\\\"name\\\":\\\"실제 카페\\\",\\\"reason\\\":\\\"휴식에 좋아요\\\"}]}]}"}]}}
                        """);

        AiGuideResponse response = client.generate(new AiGuideRequest("카페 추천", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("실제 카페");
        org.mockito.Mockito.verify(httpClient, org.mockito.Mockito.times(2)).send(
                any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void createPromptIncludesExistingScheduleAndTwoHourReservationRule() {
        AiGuideContext.Item existingItem = new AiGuideContext.Item(
                1L, "기존 점심", LocalTime.of(10, 0), null, "PLACE", null);
        AiGuideContext.Day day = new AiGuideContext.Day(
                1, LocalDate.of(2026, 8, 14), "DAY 1", null, List.of(existingItem));
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15),
                null, null, null, null, null, null, null, null, List.of(day));

        String prompt = client.createPrompt(new AiGuideRequest("빈 시간대 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()), List.of());

        assertThat(prompt)
                .contains("기존 점심 (10:00-12:00)")
                .contains("Never use a bakery, confectionery, bread shop, dessert shop, or cafe as a meal")
                .contains("Treat the retrieved detailed category as a hard venue-type constraint")
                .contains("Treat every listed window as unavailable")
                .contains("reserve two hours after its start time")
                .contains("nearest available HH:mm time")
                .contains("Never return an existing itinerary venue as a new recommendation item")
                .contains("never return a real venue already named")
                .contains("Do not create placeholder schedule items");
    }

    @Test
    void generateReturnsOnlyTheSelectedDayAndUsesOnlyThatDaysSchedule() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"DAY 1 카페\\",\\"reason\\":\\"첫째 날 추천\\"}]},{\\"day\\":2,\\"title\\":\\"DAY 2\\",\\"items\\":[{\\"time\\":\\"15:00\\",\\"name\\":\\"DAY 2 카페\\",\\"reason\\":\\"둘째 날 추천\\"}]}]}"}]}}
                """);

        AiGuideContext.Day dayOne = new AiGuideContext.Day(1, LocalDate.of(2026, 8, 14), "DAY 1", null,
                List.of(new AiGuideContext.Item(1L, "DAY 1 기존 일정", LocalTime.of(10, 0), null, "PLACE", null)));
        AiGuideContext.Day dayTwo = new AiGuideContext.Day(2, LocalDate.of(2026, 8, 15), "DAY 2", null,
                List.of(new AiGuideContext.Item(2L, "DAY 2 기존 일정", LocalTime.of(12, 0), null, "PLACE", null)));
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15),
                null, null, null, null, null, null, null, null, List.of(dayOne, dayTwo));
        AiGuideRequest request = new AiGuideRequest("둘째 날 오후 카페 추천", 1L, 2);

        String prompt = client.createPrompt(request, List.of(), new AiGuideContext(trip, List.of()), List.of());
        AiGuideResponse response = client.generate(request, List.of(), new AiGuideContext(trip, List.of()));

        assertThat(prompt).contains("Focused schedule DAY: DAY 2")
                .contains("DAY 2 기존 일정")
                .doesNotContain("DAY 1 기존 일정");
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().getFirst().day()).isEqualTo(2);
        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("DAY 2 카페");
    }

    @Test
    void generateDoesNotTreatDayTenTitleAsDayOneTitle() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 10 추천 일정\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"카페\\",\\"reason\\":\\"휴식에 좋아요\\"}]}]}"}]}}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("첫째 날 카페 추천", 1L, 1),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days()).singleElement()
                .extracting(AiGuideDayResponse::title)
                .isEqualTo("DAY 1 추천 일정");
    }

    @Test
    void generateMovesOverlappingRecommendationToNextAvailableTime() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"13:00\\",\\"name\\":\\"카페\\",\\"reason\\":\\"휴식에 좋아요\\"}]}]}"}]}}
                """);

        AiGuideContext.Item existingItem = new AiGuideContext.Item(
                1L, "기존 점심", LocalTime.of(12, 30), null, "PLACE", null);
        AiGuideContext.Day day = new AiGuideContext.Day(
                1, LocalDate.of(2026, 8, 14), "DAY 1", null, List.of(existingItem));
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15),
                null, null, null, null, null, null, null, null, List.of(day));

        AiGuideResponse response = client.generate(new AiGuideRequest("오후 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("14:30");
    }

    @Test
    void generateMovesLateRecommendationToEarlierAvailableTwoHourSlotInTripContext() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"23:00\\",\\"name\\":\\"늦은 카페\\",\\"reason\\":\\"야간 이용\\"}]}]}"}]}}
                """);

        AiGuideContext.Item existingItem = new AiGuideContext.Item(
                2L, "기존 저녁", LocalTime.of(21, 0), LocalTime.of(23, 0), "PLACE", null);
        AiGuideContext.Day day = new AiGuideContext.Day(
                1, LocalDate.of(2026, 8, 14), "DAY 1", null, List.of(existingItem));
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15),
                null, null, null, null, null, null, null, null, List.of(day));

        AiGuideResponse response = client.generate(new AiGuideRequest("23시 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("19:00");
    }

    @Test
    void generateRoundsAFreeRecommendationToTheNearestThirtyMinuteSlot() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"21:40\\",\\"name\\":\\"늦은 카페\\",\\"reason\\":\\"야간 이용\\"}]}]}"}]}}
                """);

        AiGuideContext.Day day = new AiGuideContext.Day(
                1, LocalDate.of(2026, 8, 14), "DAY 1", null, List.of());
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14),
                null, null, null, null, null, null, null, null, List.of(day));

        AiGuideResponse response = client.generate(new AiGuideRequest("늦은 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("21:30");
    }

    @Test
    void generateKeepsEarlyRecommendationWhenItDoesNotOverlap() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"05:30\\",\\"name\\":\\"이른 산책\\",\\"reason\\":\\"새벽 일정\\"}]}]}"}]}}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("이른 아침 산책", 1L),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("05:30");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        stubResponses(status, body);
    }

    @SuppressWarnings("unchecked")
    private void stubResponses(int status, String... bodies) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        String[] openAiBodies = java.util.Arrays.stream(bodies)
                .map(this::asOpenAiResponse)
                .toArray(String[]::new);
        when(response.body()).thenReturn(openAiBodies[0], java.util.Arrays.copyOfRange(openAiBodies, 1, openAiBodies.length));
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);
    }

    private String asOpenAiResponse(String body) {
        try {
            var root = new ObjectMapper().readTree(body);
            var contents = root.path("message").path("content");
            if (!contents.isArray()) {
                return body;
            }
            String text = "";
            for (var content : contents) {
                if (!content.path("text").asText().isBlank()) {
                    text = content.path("text").asText();
                    break;
                }
            }
            return new ObjectMapper().writeValueAsString(Map.of("output", List.of(Map.of(
                    "type", "message",
                    "content", List.of(Map.of("type", "output_text", "text", text))
            ))));
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
