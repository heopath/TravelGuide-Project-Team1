package org.example.all_my_trip_project.domain.ai.service;

import java.util.stream.Stream;
import java.util.List;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ChatModel;
import org.mockito.ArgumentCaptor;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiAiModelClientTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final GeminiAiModelClient client = new GeminiAiModelClient(chatModel);

    @Test
    void generateMapsGeminiJsonToCurrentGuideDto() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "answer": "A suggested itinerary",
                  "days": [{
                    "day": 1,
                    "title": "DAY 1 · City walk",
                    "items": [{"time": "10:00", "name": "Museum", "reason": "Start nearby"}]
                  }]
                }
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("Plan a day in Busan"), List.of());

        assertThat(response.answer()).isEqualTo("A suggested itinerary");
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("Museum");
        assertThat(response.externalLinks()).hasSize(2);
        assertThat(response.sources()).contains("Gemini AI");
    }

    @Test
    void generateAcceptsJsonInsideMarkdownFence() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                ```json
                {"answer":"A guide","days":[{"day":1,"title":"DAY 1","items":[{"time":"09:00","name":"Park","reason":"Fresh air"}]}]}
                ```
                """);

        AiGuideResponse response = client.generate(new AiGuideRequest("Plan a day"), List.of());

        assertThat(response.days().getFirst().title()).isEqualTo("DAY 1");
    }

    @Test
    void generateIncludesRecentConversationInGeminiPrompt() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {"answer":"A guide","days":[{"day":1,"title":"DAY 1","items":[{"time":"09:00","name":"Cafe","reason":"Nearby"}]}]}
                """);

        client.generate(
                new AiGuideRequest("음식점도 두 개 추가해줘"),
                List.of(new AiConversationTurn("광안리 카페 추천", "카페 두 곳을 추천합니다."))
        );

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue()).contains("광안리 카페 추천", "카페 두 곳을 추천합니다.");
    }

    @Test
    void generateRejectsInvalidGeminiJson() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("not-json");

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("Plan a day"), List.of()))
                .isInstanceOf(AiModelException.class)
                .hasMessage("Gemini response is not valid JSON");
    }

    @ParameterizedTest
    @MethodSource("invalidGuideResponses")
    void generateRejectsMalformedGeminiGuideResponse(String malformedResponse) {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn(malformedResponse);

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("Plan a day"), List.of()))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("Gemini response");
    }

    private static Stream<String> invalidGuideResponses() {
        return Stream.of(
                "{\"answer\":\"Guide\",\"days\":[{\"day\":0,\"title\":\"DAY 1\",\"items\":[{\"time\":\"10:00\",\"name\":\"Park\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"\",\"items\":[{\"time\":\"10:00\",\"name\":\"Park\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[{\"time\":\"25:00\",\"name\":\"Park\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[{\"time\":\"10:00\",\"name\":\"\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[{\"time\":\"10:00\",\"name\":\"Park\",\"reason\":\"\"}]}]}"
        );
    }
}
