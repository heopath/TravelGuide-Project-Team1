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
