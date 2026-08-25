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
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.example.all_my_trip_project.global.apikey.ApiKeyProvider;
import org.example.all_my_trip_project.global.apikey.ManagedApiKey;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
@Profile("ai")
public class OpenAiAiModelClient implements AiModelClient {

    private static final URI CHAT_URI = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_DAYS = 30;
    private static final int MAX_ITEMS_PER_DAY = 10;
    private static final int RECOMMENDATION_DURATION_MINUTES = 120;
    private static final int TIME_SLOT_MINUTES = 30;
    private static final int EARLIEST_RECOMMENDATION_START_MINUTES = 9 * 60;
    // 일정 화면은 종료 시각이 24:00과 같아지는 경우도 자정 초과로 취급한다.
    // AI 추천도 같은 기준을 사용하므로 2시간 체류 기준 마지막 시작 시각은 21:30이다.
    private static final int LATEST_RECOMMENDATION_START_MINUTES = 21 * 60 + 30;
    private static final String STRICT_JSON_RETRY_INSTRUCTION = """

            Your previous response did not satisfy the schedule JSON contract.
            Retry once and return only valid JSON: every day must have at least one item, and every item must have
            a non-empty HH:mm time, name, and reason. Do not include Markdown or explanatory text outside JSON.
            """;
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<AiGuideResponse.ExternalLink> DEFAULT_EXTERNAL_LINKS = List.of(
            new AiGuideResponse.ExternalLink("FLIGHT", "항공권 검색", "https://www.google.com/travel/flights"),
            new AiGuideResponse.ExternalLink("HOTEL", "숙소 검색", "https://www.google.com/travel/hotels")
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /**
     * 키를 값이 아니라 "꺼내는 방법"으로 들고 있는다. 관리자가 화면에서 키를 교체하면 재시작
     * 없이 다음 호출부터 새 키가 쓰인다.
     */
    private final Supplier<String> apiKeySupplier;
    private final String model;
    private final Duration requestTimeout;

    @Autowired
    public OpenAiAiModelClient(
            ApiKeyProvider apiKeyProvider,
            @Value("${openai.chat.model:gpt-5.6-terra}") String model,
            @Value("${openai.chat.timeout-millis:25000}") long timeoutMillis
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                new ObjectMapper(), () -> apiKeyProvider.resolve(ManagedApiKey.OPENAI),
                model, Duration.ofMillis(timeoutMillis));
    }

    /** 테스트가 키를 고정값으로 넘기던 방식을 그대로 유지한다. */
    OpenAiAiModelClient(HttpClient httpClient, ObjectMapper objectMapper, String apiKey,
                        String model, Duration requestTimeout) {
        this(httpClient, objectMapper, () -> apiKey, model, requestTimeout);
    }

    OpenAiAiModelClient(HttpClient httpClient, ObjectMapper objectMapper, Supplier<String> apiKeySupplier,
                        String model, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKeySupplier = apiKeySupplier;
        this.model = model;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                                    AiGuideContext context) {
        return generate(request, conversationHistory, context, List.of());
    }

    @Override
    public AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                                    AiGuideContext context, List<RagSearchResult> ragResults) {
        String prompt = createPrompt(request, conversationHistory, context, ragResults);
        try {
            OpenAiGuideContent content;
            try {
                content = generateContent(prompt, context, request.selectedDayNumber());
            } catch (AiModelException exception) {
                if (!isRetryableFormatFailure(exception)) {
                    throw exception;
                }
                content = generateContent(prompt + STRICT_JSON_RETRY_INSTRUCTION, context, request.selectedDayNumber());
            }

            List<String> sources = new ArrayList<>(List.of("OpenAI", "질문: " + request.question()));
            return new AiGuideResponse(content.answer(), content.days(), DEFAULT_EXTERNAL_LINKS, sources);
        } catch (AiModelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiModelException("OpenAI request failed", exception);
        }
    }

    private OpenAiGuideContent generateContent(String prompt, AiGuideContext context, Integer selectedDayNumber) {
        try {
            OpenAiGuideContent content = objectMapper.readValue(
                    extractJson(requestModel(prompt)), OpenAiGuideContent.class);
            content = normalize(content);
            content = focusOnSelectedDay(content, selectedDayNumber);
            content = moveItemsToAvailableTimes(content, context);
            validate(content);
            return content;
        } catch (JsonProcessingException exception) {
            throw new AiModelException("OpenAI response is not valid JSON", exception);
        }
    }

    private OpenAiGuideContent focusOnSelectedDay(OpenAiGuideContent content, Integer selectedDayNumber) {
        if (selectedDayNumber == null || content == null || content.days() == null) {
            return content;
        }

        AiGuideDayResponse selectedDay = content.days().stream()
                .filter(day -> day != null && day.day() == selectedDayNumber && day.items() != null && !day.items().isEmpty())
                .findFirst()
                .orElseGet(() -> content.days().stream()
                        .filter(day -> day != null && day.items() != null && !day.items().isEmpty())
                        .findFirst()
                        .map(day -> new AiGuideDayResponse(selectedDayNumber,
                                "DAY " + selectedDayNumber + " 추천 일정", day.items()))
                        .orElse(null));

        if (selectedDay == null) {
            return content;
        }

        String title = hasSelectedDayTitle(selectedDay.title(), selectedDayNumber)
                ? selectedDay.title()
                : "DAY " + selectedDayNumber + " 추천 일정";
        return new OpenAiGuideContent(content.answer(), List.of(new AiGuideDayResponse(
                selectedDayNumber, title, selectedDay.items())));
    }

    private boolean hasSelectedDayTitle(String title, Integer selectedDayNumber) {
        if (title == null || selectedDayNumber == null) {
            return false;
        }

        return title.matches("^\\s*DAY\\s+" + selectedDayNumber + "(?!\\d)(?:\\s|$).*");
    }

    private boolean isRetryableFormatFailure(AiModelException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("response is not valid JSON")
                || message.contains("invalid fenced JSON response")
                || message.contains("returned an empty response")
                || message.contains("missing guide data")
                || message.contains("invalid day")
                || message.contains("invalid schedule item"));
    }

    private String requestModel(String prompt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(CHAT_URI)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKeySupplier.get())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createRequestBody(prompt))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw openAiFailure(response);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String responseText = extractOutputText(root.path("output"));
            if (responseText == null) {
                throw new AiModelException("OpenAI returned an empty response");
            }
            return responseText;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("OpenAI request was interrupted", exception);
        } catch (IOException exception) {
            throw new AiModelException("OpenAI request failed", exception);
        }
    }

    /**
     * Keeps the API key and request body out of logs, while preserving OpenAI's
     * error type/code/message.  A status code alone is not enough to distinguish
     * a model configuration error from a quota or request-schema error.
     */
    private AiModelException openAiFailure(HttpResponse<String> response) {
        String detail = "";
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                error = root;
            }

            List<String> parts = new ArrayList<>();
            addErrorPart(parts, "type", error.path("type").asText());
            addErrorPart(parts, "code", error.path("code").asText());
            addErrorPart(parts, "message", error.path("message").asText());
            detail = String.join(", ", parts);
        } catch (JsonProcessingException ignored) {
            // Some gateway errors are not JSON. The HTTP status remains useful.
        }

        String message = "OpenAI request failed. status=" + response.statusCode();
        return detail.isBlank() ? new AiModelException(message)
                : new AiModelException(message + ", " + detail);
    }

    private void addErrorPart(List<String> parts, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = redactSecret(value.replaceAll("[\\r\\n]+", " ").trim());
        parts.add(name + "=" + normalized.substring(0, Math.min(normalized.length(), 300)));
    }

    private String redactSecret(String value) {
        // OpenAI can echo an invalid model value in an error response. If an
        // environment variable is misconfigured, that value could be an API key.
        return value.replaceAll("sk-[A-Za-z0-9_-]+", "[REDACTED]");
    }

    private String extractOutputText(JsonNode output) {
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode outputItem : output) {
            if (!"message".equals(outputItem.path("type").asText())) {
                continue;
            }
            for (JsonNode content : outputItem.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) {
                    continue;
                }
                String text = content.path("text").asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Map<String, Object> createRequestBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", "You are a Korean travel itinerary assistant.");
        body.put("input", prompt);
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "travel_itinerary",
                "strict", true,
                "schema", guideJsonSchema()
        )));
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
                "required", List.of("time", "name", "reason"),
                "additionalProperties", false
        );
        Map<String, Object> day = Map.of(
                "type", "object",
                "properties", Map.of(
                        "day", Map.of("type", "integer"),
                        "title", Map.of("type", "string"),
                        "items", Map.of("type", "array", "items", item)
                ),
                "required", List.of("day", "title", "items"),
                "additionalProperties", false
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "days", Map.of("type", "array", "items", day)
                ),
                "required", List.of("answer", "days"),
                "additionalProperties", false
        );
    }

    String createPrompt(AiGuideRequest request, List<AiConversationTurn> history,
                                AiGuideContext context, List<RagSearchResult> ragResults) {
        return """
                Create a Korean travel itinerary that answers the user question.
                Return only one JSON object matching the requested schema. Do not use Markdown.
                Every item time must be HH:mm and every day number must start at 1.
                Do not use day 0. Do not return empty titles or empty items arrays.

                Recent conversation:
                %s

                Travel context (use only provided facts):
                %s

                Retrieved place knowledge (use only if relevant; do not invent details):
                %s

                Grounding rules:
                - Name a real venue only when that exact venue name appears in Retrieved place knowledge.
                - When retrieved candidates match the requested location and broad category, use their exact venue names
                  rather than generic placeholder items. If a narrow feature such as an LP room is not documented,
                  say that the feature is unverified but still present the verified venue as a general bar/cafe option.
                - For lunch, dinner, meal, restaurant, or 맛집 requests, select only candidates whose detailed category
                  is a meal restaurant. Never use a bakery, confectionery, bread shop, dessert shop, or cafe as a meal
                  recommendation unless the user explicitly asks for that type of venue.
                - Treat the retrieved detailed category as a hard venue-type constraint: cafe/coffee requests require
                  a cafe or roastery; bakery/dessert requests require a bakery or confectionery; bar/drink requests
                  require a bar, pub, or izakaya; attraction requests require a cultural, scenic, or activity venue;
                  shopping requests require a retail venue. Do not substitute a different venue type.
                - If no matching verified place knowledge is available, do not invent a cafe, restaurant, address,
                  popularity claim, or neighborhood-specific fact. State only that verified place candidates are unavailable.
                  Do not create placeholder schedule items such as "자유 시간", "카페 탐방", or "점심 검색",
                  and do not tell the user to search a map or explore an area themselves.

                Scheduling rules:
                - Existing itinerary entries in Travel context belong to their stated DAY only.
                - If the user question explicitly names one DAY, return recommendations for that DAY only.
                - Never return an existing itinerary venue as a new recommendation item.
                - For a request asking for another or different place, never return a real venue already named
                  in Recent conversation as a new recommendation item.
                - If the user asks for another time, a time adjustment, or a different time slot for a venue,
                  keep that venue and return it at a non-overlapping available time instead of finding a new venue.
                - The existing schedule explicitly lists unavailable time windows. Treat every listed window as unavailable.
                - Never return an item time that overlaps an existing entry or another returned item on the same DAY.
                - When an existing entry has no end time, reserve two hours after its start time.
                - If the user requests a time inside an unavailable window, choose the nearest available HH:mm time
                  on the same DAY in 30-minute increments. Prefer a later time, then an earlier valid slot.
                - Reserve two hours for every returned item when checking overlaps.

                %s

                User question: %s
                """.formatted(formatHistory(history), formatContext(context, request.selectedDayNumber()),
                formatRagResults(ragResults), formatSelectedDayInstruction(request.selectedDayNumber()), request.question());
    }

    private String formatHistory(List<AiConversationTurn> history) {
        if (history == null || history.isEmpty()) return "None";
        return history.stream().map(turn -> "User: " + turn.question() + "\nAI: " + turn.answer())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String formatContext(AiGuideContext context, Integer selectedDayNumber) {
        if (context == null || context.trip() == null) return "No travel context is available.";
        AiGuideContext.Trip trip = context.trip();
        List<AiGuideContext.Day> days = trip.days();
        if (selectedDayNumber != null && days != null) {
            days = days.stream().filter(day -> Integer.valueOf(selectedDayNumber).equals(day.dayNumber())).toList();
        }
        return "destination=" + trip.destinationName() + ", dates=" + trip.startDate() + " to " + trip.endDate()
                + ", purpose=" + trip.purpose() + ", existing schedule=" + formatSchedule(days);
    }

    private String formatSelectedDayInstruction(Integer selectedDayNumber) {
        if (selectedDayNumber == null) {
            return "No schedule DAY was selected. Return only the DAYs needed to answer the user question.";
        }
        return "Focused schedule DAY: DAY " + selectedDayNumber + ". Return exactly one day object with day="
                + selectedDayNumber + ". Use only this DAY's existing schedule when checking unavailable times.";
    }

    private String formatSchedule(List<AiGuideContext.Day> days) {
        if (days == null || days.isEmpty()) return "None";
        return days.stream().map(day -> "DAY " + day.dayNumber() + ": " + day.items().stream()
                        .map(this::formatScheduleItem)
                        .collect(java.util.stream.Collectors.joining(", ")))
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String formatScheduleItem(AiGuideContext.Item item) {
        if (item.startTime() == null) return item.title() + " (time unset)";
        String end = item.endTime() == null
                ? item.startTime().plusHours(2).toString()
                : item.endTime().toString();
        return item.title() + " (" + item.startTime() + "-" + end + ")";
    }

    private String formatRagResults(List<RagSearchResult> ragResults) {
        if (ragResults == null || ragResults.isEmpty()) return "None";
        return ragResults.stream()
                .map(result -> "Source: " + result.source() + "\n" + result.content())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String extractJson(String response) {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.isBlank()) throw new AiModelException("OpenAI returned an empty response");
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw new AiModelException("OpenAI returned an invalid fenced JSON response");
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private OpenAiGuideContent normalize(OpenAiGuideContent content) {
        if (content == null || content.days() == null) {
            return content;
        }
        List<AiGuideDayResponse> days = new ArrayList<>();
        for (int index = 0; index < content.days().size(); index++) {
            AiGuideDayResponse day = content.days().get(index);
            if (day == null) {
                days.add(null);
                continue;
            }
            int dayNumber = day.day() < 1 ? index + 1 : day.day();
            String title = day.title() == null || day.title().isBlank()
                    ? "DAY " + dayNumber + " 추천 일정"
                    : day.title().trim();
            days.add(new AiGuideDayResponse(dayNumber, title, day.items()));
        }
        return new OpenAiGuideContent(content.answer(), days);
    }

    /**
     * OpenAI is instructed not to overlap existing plans, but the final response is also adjusted here
     * so that an addable recommendation is presented whenever the same DAY has an available time slot.
     */
    private OpenAiGuideContent moveItemsToAvailableTimes(OpenAiGuideContent content, AiGuideContext context) {
        if (content == null || content.days() == null || context == null || context.trip() == null) {
            return content;
        }

        Map<Integer, List<TimeWindow>> occupiedByDay = existingOccupiedWindows(context.trip().days());
        List<AiGuideDayResponse> adjustedDays = new ArrayList<>();

        for (AiGuideDayResponse day : content.days()) {
            if (day == null || day.items() == null) {
                adjustedDays.add(day);
                continue;
            }

            List<TimeWindow> occupied = occupiedByDay.computeIfAbsent(day.day(), ignored -> new ArrayList<>());
            List<AiGuideItemResponse> adjustedItems = new ArrayList<>();
            for (AiGuideItemResponse item : day.items()) {
                AiGuideItemResponse adjustedItem = moveItemToAvailableTime(item, occupied);
                if (adjustedItem != null) {
                    adjustedItems.add(adjustedItem);
                    addRecommendationWindow(adjustedItem, occupied);
                }
            }
            if (!adjustedItems.isEmpty()) {
                adjustedDays.add(new AiGuideDayResponse(day.day(), day.title(), adjustedItems));
            }
        }
        return new OpenAiGuideContent(content.answer(), adjustedDays);
    }

    private Map<Integer, List<TimeWindow>> existingOccupiedWindows(List<AiGuideContext.Day> days) {
        Map<Integer, List<TimeWindow>> occupiedByDay = new LinkedHashMap<>();
        if (days == null) {
            return occupiedByDay;
        }
        for (AiGuideContext.Day day : days) {
            if (day == null || day.dayNumber() == null) {
                continue;
            }
            List<TimeWindow> occupied = occupiedByDay.computeIfAbsent(day.dayNumber(), ignored -> new ArrayList<>());
            for (AiGuideContext.Item item : day.items()) {
                if (item == null || item.startTime() == null) {
                    continue;
                }
                int start = toMinutes(item.startTime());
                int end = item.endTime() == null ? start + RECOMMENDATION_DURATION_MINUTES : toMinutes(item.endTime());
                if (end > start) {
                    occupied.add(new TimeWindow(start, Math.min(end, 24 * 60)));
                }
            }
        }
        return occupiedByDay;
    }

    private AiGuideItemResponse moveItemToAvailableTime(AiGuideItemResponse item, List<TimeWindow> occupied) {
        if (item == null || item.time() == null || !TIME_PATTERN.matcher(item.time()).matches()) {
            return item;
        }
        int requestedStart = toMinutes(LocalTime.parse(item.time(), TIME_FORMATTER));
        if (isAvailable(requestedStart, occupied)) {
            return item;
        }

        Integer availableStart = findAvailableStart(requestedStart, occupied);
        if (availableStart == null) {
            return null;
        }
        return new AiGuideItemResponse(
                formatMinutes(availableStart), item.name(), item.reason(), item.placeId(),
                item.placeCategory(), item.placeAddress(), item.placeUrl()
        );
    }

    private void addRecommendationWindow(AiGuideItemResponse item, List<TimeWindow> occupied) {
        if (item == null || item.time() == null || !TIME_PATTERN.matcher(item.time()).matches()) {
            return;
        }
        int start = toMinutes(LocalTime.parse(item.time(), TIME_FORMATTER));
        occupied.add(new TimeWindow(start, Math.min(start + RECOMMENDATION_DURATION_MINUTES, 24 * 60)));
    }

    private Integer findAvailableStart(int requestedStart, List<TimeWindow> occupied) {
        int firstCandidate = roundUpToTimeSlot(requestedStart);
        for (int candidate = firstCandidate; candidate <= LATEST_RECOMMENDATION_START_MINUTES; candidate += TIME_SLOT_MINUTES) {
            if (isAvailable(candidate, occupied)) {
                return candidate;
            }
        }

        int lastEarlierCandidate = Math.min(roundUpToTimeSlot(requestedStart) - TIME_SLOT_MINUTES,
                LATEST_RECOMMENDATION_START_MINUTES);
        for (int candidate = lastEarlierCandidate;
             candidate >= EARLIEST_RECOMMENDATION_START_MINUTES;
             candidate -= TIME_SLOT_MINUTES) {
            if (isAvailable(candidate, occupied)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isAvailable(int start, List<TimeWindow> occupied) {
        int end = start + RECOMMENDATION_DURATION_MINUTES;
        return start <= LATEST_RECOMMENDATION_START_MINUTES
                && start % TIME_SLOT_MINUTES == 0
                && end < 24 * 60
                && occupied.stream().noneMatch(window -> start < window.end() && window.start() < end);
    }

    private int roundUpToTimeSlot(int minutes) {
        return ((minutes + TIME_SLOT_MINUTES - 1) / TIME_SLOT_MINUTES) * TIME_SLOT_MINUTES;
    }

    private int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private String formatMinutes(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60).format(TIME_FORMATTER);
    }

    private void validate(OpenAiGuideContent content) {
        if (content == null || content.answer() == null || content.answer().isBlank()
                || content.days() == null || content.days().isEmpty() || content.days().size() > MAX_DAYS) {
            throw new AiModelException("OpenAI response is missing guide data");
        }
        for (int index = 0; index < content.days().size(); index++) {
            AiGuideDayResponse day = content.days().get(index);
            if (day == null || day.day() < 1 || day.title() == null || day.title().isBlank()
                    || day.items() == null || day.items().isEmpty() || day.items().size() > MAX_ITEMS_PER_DAY) {
                throw new AiModelException("OpenAI response has an invalid day at index " + index);
            }
            for (AiGuideItemResponse item : day.items()) {
                if (item == null || item.time() == null || !TIME_PATTERN.matcher(item.time()).matches()
                        || item.name() == null || item.name().isBlank()
                        || item.reason() == null || item.reason().isBlank()) {
                    throw new AiModelException("OpenAI response has an invalid schedule item");
                }
            }
        }
    }

    private record OpenAiGuideContent(String answer, List<AiGuideDayResponse> days) {
    }

    private record TimeWindow(int start, int end) {
    }
}
