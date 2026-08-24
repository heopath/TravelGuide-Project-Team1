package org.example.all_my_trip_project.domain.support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상담 채팅 전용 Gemini 클라이언트.
 *
 * <p><b>{@code domain.ai}의 {@code AiModelClient}와 별개다.</b> 설계 문서(§1)는 원래 Spring AI의
 * {@code ChatModel} 빈을 공유하는 방향을 전제했지만, 어떤 프로필 조합에서도
 * {@code spring.ai.model.chat=none}이라 그 빈이 뜨지 않는다. 여행 가이드가 실제로 쓰는 구현체는
 * {@code OpenAiAiModelClient}이고 그쪽도 REST를 직접 부른다.
 *
 * <p>그래서 이 클래스는 저장소에서 실제로 동작이 확인된 Gemini 경로인
 * {@code AiTripPlanService}의 패턴(REST API 직접 호출, {@code gemini.api-key})을 그대로 따른다.
 * 갈래를 하나로 모으는 문제는 이슈 #382에서 다룬다.
 */
@Service
@Slf4j
public class SupportChatBotClient {

    private static final String MODEL_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    /** 손님이 답을 기다리는 자리라 오래 걸리면 그 자체가 "시간 초과 → WAITING" 대상이다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** 봇 스스로 상담원에게 넘겨야 한다고 판단하면 응답 끝에 이 표시를 붙이도록 프롬프트에서 시킨다. */
    private static final String HANDOFF_MARKER = "[HANDOFF]";

    /** 방을 막 연 직후, 아직 손님 말이 없을 때 첫 인사를 이끌어내는 내부 신호. 손님에게는 안 보인다. */
    private static final String GREETING_KICKOFF =
            "(상담 시작 — 아직 손님이 아무 말도 하지 않았습니다. 이 문장에 답하지 말고, 먼저 인사하며 "
                    + "무엇을 도와드릴지 물어보세요.)";

    private static final String SYSTEM_PROMPT = """
            당신은 여행 플래너 서비스 "올마이트립스"의 고객 상담 챗봇입니다. 한국어로, 짧고
            친절하게 답합니다.

            답할 수 있는 범위: 여행 예약/결제/취소, 일정(여행·일차·일정) 관리, 티켓 예매·QR
            입장, 계정·마이페이지 사용법 등 이 서비스 이용과 직접 관련된 질문입니다.

            아래 중 하나에 해당하면, 손님에게 보여줄 답변 마지막 줄에 다른 내용 없이 정확히
            "%s" 한 줄만 추가하세요.
            - 손님이 상담원 연결을 명시적으로 요청함
            - 이 서비스와 무관하거나 개인정보·결제 정보 등 민감한 처리가 필요해 챗봇이 답하면
              안 되는 질문
            - 같은 문제를 여러 번 물었는데도 대화 내역상 해결되지 않고 있음

            실제 예약 내역을 조회하거나 변경할 수는 없습니다. 확정되지 않은 정보를 사실인 것처럼
            답하지 마세요. 괄호로 시작하는 안내 문장은 손님이 보낸 말이 아니라 내부 지시입니다.
            """.formatted(HANDOFF_MARKER);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.5-flash}")
    private String geminiModel;

    public SupportChatBotClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isAvailable() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    /**
     * 대화 내역을 넘기면 다음 봇 발화를 받는다.
     *
     * @param conversation 시간순 대화 내역(USER/BOT만, 방금 저장된 손님 메시지 포함). 방을 막
     *                      연 직후라 아직 아무 말도 없으면 빈 목록을 넘기고, 봇이 첫 인사를
     *                      스스로 시작한다.
     * @throws SupportChatBotException API 키가 없거나, 호출이 실패·시간 초과했거나, 응답 형식이
     *                                  올바르지 않을 때. 호출부는 이 예외를 방을 {@code WAITING}
     *                                  으로 넘기는 신호로 쓴다.
     */
    public SupportChatBotReply reply(List<SupportChatMessageDTO> conversation) {
        if (!isAvailable()) {
            throw new SupportChatBotException("상담 봇 API 키가 설정돼 있지 않습니다.");
        }
        try {
            String responseBody = restClient.post()
                    .uri(MODEL_ENDPOINT.formatted(geminiModel))
                    .header("x-goog-api-key", geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                            "contents", toContents(conversation)
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
            String text = response == null
                    ? ""
                    : response.at("/candidates/0/content/parts/0/text").asText();
            if (text.isBlank()) {
                throw new SupportChatBotException("상담 봇이 응답을 반환하지 않았습니다.");
            }
            return parse(text);
        } catch (SupportChatBotException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("상담 봇 Gemini 호출에 실패했습니다.", exception);
            throw new SupportChatBotException("상담 봇 호출에 실패했습니다.", exception);
        } catch (Exception exception) {
            log.warn("상담 봇 응답 처리에 실패했습니다.", exception);
            throw new SupportChatBotException("상담 봇 응답을 처리하지 못했습니다.", exception);
        }
    }

    private List<Map<String, Object>> toContents(List<SupportChatMessageDTO> conversation) {
        List<Map<String, Object>> contents = conversation.stream()
                .filter(message -> "USER".equals(message.getSenderType()) || "BOT".equals(message.getSenderType()))
                .map(message -> Map.<String, Object>of(
                        "role", "USER".equals(message.getSenderType()) ? "user" : "model",
                        "parts", List.of(Map.of("text", message.getContent()))
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        if (contents.isEmpty()) {
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", GREETING_KICKOFF))));
        }
        return contents;
    }

    /** package-private: {@link SupportChatBotClientTest}가 네트워크 호출 없이 표시 파싱만 검증한다. */
    SupportChatBotReply parse(String rawText) {
        String trimmed = rawText.strip();
        boolean handoff = trimmed.endsWith(HANDOFF_MARKER);
        String content = handoff
                ? trimmed.substring(0, trimmed.length() - HANDOFF_MARKER.length()).strip()
                : trimmed;
        if (content.isBlank()) {
            content = "상담원에게 연결해 드릴게요. 잠시만 기다려 주세요.";
        }
        return new SupportChatBotReply(content, handoff);
    }
}
