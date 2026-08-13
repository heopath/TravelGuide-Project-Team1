package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiAiModelClientTest {
    private final ChatModel chatModel = mock(ChatModel.class);
    private final GeminiAiModelClient client = new GeminiAiModelClient(chatModel);

    @Test
    void generateMapsGeminiJsonToCurrentGuideDto() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn(validGuideJson());

        AiGuideResponse response = client.generate(request(), List.of(), emptyContext());

        assertThat(response.answer()).isEqualTo("A suggested itinerary");
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().getFirst().items().getFirst().name()).isEqualTo("Museum");
        assertThat(response.externalLinks()).hasSize(2);
    }

    @Test
    void generateIncludesRecentConversationAndTripContextInGeminiPrompt() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn(validGuideJson());
        AiGuideContext context = new AiGuideContext(
                new AiGuideContext.Trip(12L, "Busan trip", "Busan", LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 11), "FRIENDS", 2, "FOOD", null, "KRW", "SUBWAY",
                        "SEAFOOD", "RELAXED", "HOTEL", List.of(new AiGuideContext.Day(1,
                        LocalDate.of(2026, 8, 10), "Arrival", "Near Gwangalli", List.of(
                        new AiGuideContext.Item(null, "Gwangalli dinner", null, null, "FOOD", "Seafood"))))),
                List.of(new AiGuideContext.Preference("FOOD", "Food travel", (short) 5))
        );

        client.generate(request(), List.of(new AiConversationTurn("Recommend a cafe", "Try a cafe nearby")), context);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue()).contains(
                "Recommend a cafe", "Busan", "Food travel", "Near Gwangalli", "Gwangalli dinner",
                "count the candidates exactly from top to bottom", "Do not choose a different candidate"
        );
    }

    @Test
    void generateRejectsInvalidGeminiJson() {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("not-json");

        assertThatThrownBy(() -> client.generate(request(), List.of(), emptyContext()))
                .isInstanceOf(AiModelException.class)
                .hasMessage("Gemini response is not valid JSON");
    }

    @ParameterizedTest
    @MethodSource("invalidGuideResponses")
    void generateRejectsMalformedGeminiGuideResponse(String malformedResponse) {
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn(malformedResponse);

        assertThatThrownBy(() -> client.generate(request(), List.of(), emptyContext()))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("Gemini response");
    }

    private AiGuideRequest request() {
        return new AiGuideRequest("Plan a day in Busan", null);
    }

    private AiGuideContext emptyContext() {
        return new AiGuideContext(null, List.of());
    }

    private String validGuideJson() {
        return "{\"answer\":\"A suggested itinerary\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[{\"time\":\"10:00\",\"name\":\"Museum\",\"reason\":\"Start nearby\"}]}]}";
    }

    private static Stream<String> invalidGuideResponses() {
        return Stream.of(
                "{\"answer\":\"Guide\",\"days\":[{\"day\":0,\"title\":\"DAY 1\",\"items\":[{\"time\":\"10:00\",\"name\":\"Park\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"\",\"items\":[{\"time\":\"10:00\",\"name\":\"Park\",\"reason\":\"Near\"}]}]}",
                "{\"answer\":\"Guide\",\"days\":[{\"day\":1,\"title\":\"DAY 1\",\"items\":[]}]}"
        );
    }
}
