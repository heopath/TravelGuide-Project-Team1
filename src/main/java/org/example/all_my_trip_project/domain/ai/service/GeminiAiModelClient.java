package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;

@Component
@Profile("ai")
public class GeminiAiModelClient implements AiModelClient {

    private static final ExecutorService MODEL_CALL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final List<AiGuideResponse.ExternalLink> DEFAULT_EXTERNAL_LINKS = List.of(
            new AiGuideResponse.ExternalLink("FLIGHT", "항공권 검색", "https://www.google.com/travel/flights"),
            new AiGuideResponse.ExternalLink("HOTEL", "숙소 검색", "https://www.google.com/travel/hotels")
    );

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration requestTimeout;

    public GeminiAiModelClient(
            ChatModel chatModel,
            @Value("${ai.guide.gemini.timeout-seconds:30}") long timeoutSeconds
    ) {
        this(chatModel, Duration.ofSeconds(timeoutSeconds));
    }

    GeminiAiModelClient(ChatModel chatModel, Duration requestTimeout) {
        this.chatModel = chatModel;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public AiGuideResponse generate(AiGuideRequest request) {
        try {
            String response = requestModel(createPrompt(request.question()));
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
        Future<String> future = MODEL_CALL_EXECUTOR.submit(() -> chatModel.call(prompt));
        try {
            return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AiModelException("Gemini request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("Gemini request interrupted", exception);
        } catch (ExecutionException exception) {
            throw new AiModelException("Gemini request failed", exception.getCause());
        }
    }

    private String createPrompt(String question) {
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

                사용자 질문: %s
                """.formatted(question);
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
                || content.days() == null || content.days().isEmpty()) {
            throw new AiModelException("Gemini response is missing guide data");
        }
    }

    private record GeminiGuideContent(
            String answer,
            List<AiGuideDayResponse> days
    ) {
    }
}
