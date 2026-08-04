package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

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

        AiGuideResponse response = client.generate(new AiGuideRequest("Plan a day in Busan"));

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

        AiGuideResponse response = client.generate(new AiGuideRequest("Plan a day"));

        assertThat(response.days().getFirst().title()).isEqualTo("DAY 1");
    }

    @Test
    void generateRejectsInvalidGeminiJson() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("not-json");

        assertThatThrownBy(() -> client.generate(new AiGuideRequest("Plan a day")))
                .isInstanceOf(AiModelException.class)
                .hasMessage("Gemini response is not valid JSON");
    }
}
