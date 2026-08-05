package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Profile("ai")
public class GeminiAiModelClient implements AiModelClient {

    private static final int MAX_DAYS = 30;
    private static final int MAX_ITEMS_PER_DAY = 10;
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private static final List<AiGuideResponse.ExternalLink> DEFAULT_EXTERNAL_LINKS = List.of(
            new AiGuideResponse.ExternalLink("FLIGHT", "항공권 검색", "https://www.google.com/travel/flights"),
            new AiGuideResponse.ExternalLink("HOTEL", "숙소 검색", "https://www.google.com/travel/hotels")
    );

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public GeminiAiModelClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                                    AiGuideContext context) {
        try {
            String response = requestModel(createPrompt(request.question(), conversationHistory)
                    + "\n\nTravel context (use only provided facts; do not invent missing facts):\n"
                    + formatContext(context));
            GeminiGuideContent content = objectMapper.readValue(extractJson(response), GeminiGuideContent.class);
            validate(content);

            return new AiGuideResponse(
                    content.answer(),
                    content.days(),
                    DEFAULT_EXTERNAL_LINKS,
                    List.of("Gemini AI", "질문: " + request.question())
            );
        } catch (JsonProcessingException exception) {
            throw new AiModelException("Gemini response is not valid JSON", exception);
        } catch (AiModelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiModelException("Gemini request failed", exception);
        }
    }

    private String requestModel(String prompt) {
        try {
            return chatModel.call(prompt);
        } catch (Exception exception) {
            throw new AiModelException("Gemini request failed", exception);
        }
    }

    private String createPrompt(String question, List<AiConversationTurn> conversationHistory) {
        String history = conversationHistory == null || conversationHistory.isEmpty()
                ? "이전 대화 없음"
                : conversationHistory.stream()
                .map(turn -> "사용자: " + turn.question() + "\nAI: " + turn.answer())
                .collect(java.util.stream.Collectors.joining("\n\n"));

        return """
                당신은 한국 여행 일정 추천 도우미입니다.
                사용자의 질문에 맞는 현실적인 여행 일정을 추천하세요.

                반드시 Markdown 설명 없이 아래 JSON 형식만 반환하세요.
                time은 HH:mm 형식, day는 1부터 시작하는 정수여야 합니다.

                {
                  "answer": "추천을 간단히 설명하는 문장",
                  "days": [
                    {
                      "day": 1,
                      "title": "DAY 1 · 일정 제목",
                      "items": [
                        {
                          "time": "10:00",
                          "name": "장소 또는 활동 이름",
                          "reason": "추천 이유"
                        }
                      ]
                    }
                  ]
                }

                이전 대화:
                %s

                사용자 질문: %s
                """.formatted(history, question);
    }

    private String formatContext(AiGuideContext context) {
        if (context == null) return "No travel or preference context is available.";

        String preferences = context.preferences().isEmpty()
                ? "none"
                : context.preferences().stream()
                .map(preference -> preference.name() + " (score=" + preference.score() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        if (context.trip() == null) return "User preferences: " + preferences;

        AiGuideContext.Trip trip = context.trip();
        String days = trip.days().stream()
                .map(day -> "DAY " + day.dayNumber() + " " + day.tripDate()
                        + (day.title() == null ? "" : " - " + day.title())
                        + (day.memo() == null || day.memo().isBlank() ? "" : " (" + day.memo() + ")")
                        + ", items=" + day.items().stream()
                        .map(item -> item.startTime() + " " + item.title()
                                + (item.memo() == null || item.memo().isBlank() ? "" : " (" + item.memo() + ")"))
                        .collect(java.util.stream.Collectors.joining(", ")))
                .collect(java.util.stream.Collectors.joining(", "));
        return "Trip id=" + trip.tripId()
                + ", destination=" + trip.destinationName()
                + ", dates=" + trip.startDate() + " to " + trip.endDate()
                + ", companion=" + trip.companionType()
                + ", purpose=" + trip.purpose()
                + ", food preference=" + trip.foodPreference()
                + ", transport=" + trip.transportPreference()
                + ", existing days=" + days
                + ", user preferences=" + preferences;
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new AiModelException("Gemini returned an empty response");
        }

        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw new AiModelException("Gemini returned an invalid fenced JSON response");
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private void validate(GeminiGuideContent content) {
        if (content == null || content.answer() == null || content.answer().isBlank()
                || content.days() == null || content.days().isEmpty() || content.days().size() > MAX_DAYS) {
            throw new AiModelException("Gemini response is missing guide data");
        }
        for (AiGuideDayResponse day : content.days()) {
            if (day == null || day.day() < 1 || day.title() == null || day.title().isBlank()
                    || day.items() == null || day.items().isEmpty() || day.items().size() > MAX_ITEMS_PER_DAY) {
                throw new AiModelException("Gemini response has an invalid day");
            }
            for (AiGuideItemResponse item : day.items()) {
                if (item == null || item.time() == null || !TIME_PATTERN.matcher(item.time()).matches()
                        || item.name() == null || item.name().isBlank()
                        || item.reason() == null || item.reason().isBlank()) {
                    throw new AiModelException("Gemini response has an invalid schedule item");
                }
            }
        }
    }

    private record GeminiGuideContent(
            String answer,
            List<AiGuideDayResponse> days
    ) {
    }
}
