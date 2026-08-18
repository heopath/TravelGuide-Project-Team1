package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CohereAiModelClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final CohereAiModelClient client = new CohereAiModelClient(
            httpClient, new ObjectMapper(), "test-key", "command-a-plus-05-2026", Duration.ofSeconds(25));

    @Test
    void generateMapsCohereJsonToCurrentGuideDto() throws Exception {
        stubResponse(200, """
                {"message":{"content":[{"type":"text","text":"{\\"answer\\":\\"추천 일정\\",\\"days\\":[{\\"day\\":1,\\"title\\":\\"DAY 1\\",\\"items\\":[{\\"time\\":\\"10:00\\",\\"name\\":\\"해운대\\",\\"reason\\":\\"바다 산책에 좋아요\\"}]}]}"}]}}
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("부산 하루 일정 추천", null),
                List.of(), new AiGuideContext(null, List.of()));

        assertThat(response.answer()).isEqualTo("추천 일정");
        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("해운대");
        assertThat(response.sources()).contains("Cohere AI");
    }

    @Test
    void generateRejectsCohereErrorResponse() throws Exception {
        stubResponse(429, "{\"message\":\"rate limit\"}");

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("부산 하루 일정 추천", null),
                List.of(), new AiGuideContext(null, List.of())))
                .isInstanceOf(AiModelException.class)
                .hasMessage("Cohere request failed. status=429");
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
    void createPromptIncludesExistingScheduleAndTwoHourReservationRule() {
        AiGuideContext.Item existingItem = new AiGuideContext.Item(
                1L, "기존 점심", LocalTime.of(10, 0), null, "PLACE", null);
        AiGuideContext.Day day = new AiGuideContext.Day(
                1, LocalDate.of(2026, 8, 14), "DAY 1", null, List.of(existingItem));
        AiGuideContext.Trip trip = new AiGuideContext.Trip(
                1L, "부산 여행", "부산", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15),
                null, null, null, null, null, null, null, null, null, List.of(day));

        String prompt = client.createPrompt(new AiGuideRequest("빈 시간대 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()), List.of());

        assertThat(prompt)
                .contains("기존 점심 (10:00-12:00)")
                .contains("Treat every listed window as unavailable")
                .contains("reserve two hours after its start time")
                .contains("nearest later available HH:mm time");
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
                null, null, null, null, null, null, null, null, null, List.of(day));

        AiGuideResponse response = client.generate(new AiGuideRequest("오후 카페 추천", 1L),
                List.of(), new AiGuideContext(trip, List.of()));

        assertThat(response.days().getFirst().items().getFirst().time()).isEqualTo("14:30");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);
    }
}
