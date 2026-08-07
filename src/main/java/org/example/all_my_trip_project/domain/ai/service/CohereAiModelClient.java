package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.rag.dto.RagPlaceResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Profile("ai")
public class CohereAiModelClient implements AiModelClient {

    private static final URI CHAT_URI = URI.create("https://api.cohere.com/v2/chat");
    private static final int MAX_DAYS = 30;
    private static final int MAX_ITEMS_PER_DAY = 10;
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final List<AiGuideResponse.ExternalLink> DEFAULT_EXTERNAL_LINKS = List.of(
            new AiGuideResponse.ExternalLink("FLIGHT", "항공권 검색", "https://www.google.com/travel/flights"),
            new AiGuideResponse.ExternalLink("HOTEL", "숙소 검색", "https://www.google.com/travel/hotels")
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Duration requestTimeout;

    @Autowired
    public CohereAiModelClient(
            @Value("${cohere.api-key}") String apiKey,
            @Value("${cohere.chat.model:command-a-plus-05-2026}") String model,
            @Value("${cohere.chat.timeout-millis:25000}") long timeoutMillis
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                new ObjectMapper(), apiKey, model, Duration.ofMillis(timeoutMillis));
    }

    CohereAiModelClient(HttpClient httpClient, ObjectMapper objectMapper, String apiKey,
                        String model, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                                    AiGuideContext context, List<RagPlaceResult> ragPlaces) {
        try {
            CohereGuideContent content = objectMapper.readValue(
                    extractJson(requestModel(createPrompt(request, conversationHistory, context, ragPlaces))),
                    CohereGuideContent.class
            );
            validate(content);

            List<String> sources = new ArrayList<>(List.of("Cohere AI", "질문: " + request.question()));
            if (ragPlaces != null) {
                ragPlaces.stream()
                        .map(RagPlaceResult::name)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .forEach(name -> sources.add("RAG 장소: " + name));
            }
            return new AiGuideResponse(content.answer(), content.days(), DEFAULT_EXTERNAL_LINKS, sources);
        } catch (JsonProcessingException exception) {
            throw new AiModelException("Cohere response is not valid JSON", exception);
        } catch (AiModelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiModelException("Cohere request failed", exception);
        }
    }

    private String requestModel(String prompt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(CHAT_URI)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("X-Client-Name", "all-my-trips")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createRequestBody(prompt))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiModelException("Cohere request failed. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("message").path("content");
            String responseText = extractTextContent(content);
            if (responseText == null) {
                throw new AiModelException("Cohere returned an empty response");
            }
            return responseText;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("Cohere request was interrupted", exception);
        } catch (IOException exception) {
            throw new AiModelException("Cohere request failed", exception);
        }
    }

    private String extractTextContent(JsonNode content) {
        if (!content.isArray()) {
            return null;
        }
        for (JsonNode block : content) {
            String text = block.path("text").asText();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Map<String, Object> createRequestBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a Korean travel itinerary assistant."),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("response_format", Map.of("type", "json_object", "schema", guideJsonSchema()));
        return body;
    }

    private Map<String, Object> guideJsonSchema() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "properties", Map.of(
                        "time", Map.of("type", "string"),
                        "name", Map.of("type", "string"),
                        "reason", Map.of("type", "string")
                ),
                "required", List.of("time", "name", "reason")
        );
        Map<String, Object> day = Map.of(
                "type", "object",
                "properties", Map.of(
                        "day", Map.of("type", "integer"),
                        "title", Map.of("type", "string"),
                        "items", Map.of("type", "array", "items", item)
                ),
                "required", List.of("day", "title", "items")
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "days", Map.of("type", "array", "items", day)
                ),
                "required", List.of("answer", "days")
        );
    }

    private String createPrompt(AiGuideRequest request, List<AiConversationTurn> history,
                                AiGuideContext context, List<RagPlaceResult> ragPlaces) {
        return """
                Create a Korean travel itinerary that answers the user question.
                Return only one JSON object matching the requested schema. Do not use Markdown.
                Every item time must be HH:mm and every day number must start at 1.

                Recent conversation:
                %s

                Travel context (use only provided facts):
                %s

                Retrieved public place facts (use only if relevant):
                %s

                User question: %s
                """.formatted(formatHistory(history), formatContext(context), formatRagPlaces(ragPlaces), request.question());
    }

    private String formatHistory(List<AiConversationTurn> history) {
        if (history == null || history.isEmpty()) return "None";
        return history.stream().map(turn -> "User: " + turn.question() + "\nAI: " + turn.answer())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String formatContext(AiGuideContext context) {
        if (context == null || context.trip() == null) return "No travel context is available.";
        AiGuideContext.Trip trip = context.trip();
        return "destination=" + trip.destinationName() + ", dates=" + trip.startDate() + " to " + trip.endDate()
                + ", purpose=" + trip.purpose() + ", existing days=" + trip.days();
    }

    private String formatRagPlaces(List<RagPlaceResult> ragPlaces) {
        if (ragPlaces == null || ragPlaces.isEmpty()) return "None";
        return ragPlaces.stream().map(place -> "name=" + place.name() + ", category=" + place.category()
                        + ", address=" + place.address() + ", facts=" + place.description())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String extractJson(String response) {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.isBlank()) throw new AiModelException("Cohere returned an empty response");
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw new AiModelException("Cohere returned an invalid fenced JSON response");
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private void validate(CohereGuideContent content) {
        if (content == null || content.answer() == null || content.answer().isBlank()
                || content.days() == null || content.days().isEmpty() || content.days().size() > MAX_DAYS) {
            throw new AiModelException("Cohere response is missing guide data");
        }
        for (AiGuideDayResponse day : content.days()) {
            if (day == null || day.day() < 1 || day.title() == null || day.title().isBlank()
                    || day.items() == null || day.items().isEmpty() || day.items().size() > MAX_ITEMS_PER_DAY) {
                throw new AiModelException("Cohere response has an invalid day");
            }
            for (AiGuideItemResponse item : day.items()) {
                if (item == null || item.time() == null || !TIME_PATTERN.matcher(item.time()).matches()
                        || item.name() == null || item.name().isBlank()
                        || item.reason() == null || item.reason().isBlank()) {
                    throw new AiModelException("Cohere response has an invalid schedule item");
                }
            }
        }
    }

    private record CohereGuideContent(String answer, List<AiGuideDayResponse> days) {
    }
}
