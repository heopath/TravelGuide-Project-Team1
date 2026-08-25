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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상담 채팅 전용 Gemini 클라이언트.
 *
 * <p>여행 AI 가이드는 {@code OpenAiAiModelClient}로 OpenAI REST API를 호출하고, 고객센터 챗봇은
 * 이 클래스에서 Gemini REST API를 직접 호출한다. 두 기능은 프롬프트·응답 형식·장애 처리 정책이
 * 달라 각각의 전용 클라이언트를 유지한다. Spring AI는 대화 모델 추상화가 아닌 RAG의 임베딩 및
 * {@code PgVectorStore} 보조 용도로만 사용한다.
 */
@Service
@Slf4j
public class SupportChatBotClient {

    private static final String MODEL_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    /** 손님이 답을 기다리는 자리라 무한정 기다리지 않도록 호출 시간을 제한한다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** AI가 상담원 연결을 실행하거나 사용자에게 재확인해야 한다고 판단할 때 쓰는 내부 표시다. */
    private static final String HANDOFF_MARKER = "[HANDOFF]";
    private static final String CONFIRM_HANDOFF_MARKER = "[CONFIRM_HANDOFF]";
    /**
     * 모든 내부 표시(화면 이동·장소 추천·상담원 연결)를 한 정규식으로 찾는다.
     *
     * <p>이전에는 표시별로 정규식이 달라 "줄 전체가 표시 하나뿐" 또는 "문자열 끝이 표시로
     * 끝난다"는 가정에 기댔다. 실제로는 Gemini가 그 가정을 어기는 경우가 있다 — 예를 들어
     * 같은 줄에 {@code [CONFIRM_HANDOFF] [ACTION:SUPPORT]}처럼 붙여 쓰면 두 정규식 모두
     * 매치하지 못해 대괄호 표시가 그대로 손님 화면에 노출된다(실사용 중 발견). 그래서 표시가
     * 어느 줄의 어느 위치에 있든, 다른 표시와 같은 줄에 있든 찾아내도록 위치 가정 없이
     * 전체 본문에서 찾는다.
     */
    private static final Pattern MARKER = Pattern.compile(
            "\\[(?:ACTION:(?<action>[A-Z_]+)"
                    + "|PLACE:(?<place>\\d+)(?:\\|(?<reason>[^]\\r\\n]{1,160}))?"
                    + "|(?<confirm>CONFIRM_HANDOFF)"
                    + "|(?<handoff>HANDOFF))]");
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "NEW_TRIP", "MY_TRIPS", "TRIP_SCHEDULE", "RECOMMENDED_PLACES",
            "BOOK_FLIGHT", "BOOK_HOTEL", "BOOK_TICKET", "MY_BOOKINGS", "MY_TICKETS",
            "FAVORITES", "REVIEWS", "NOTIFICATIONS", "ACCOUNT_SETTINGS", "SUPPORT");

    /** 방을 막 연 직후, 아직 손님 말이 없을 때 첫 인사를 이끌어내는 내부 신호. 손님에게는 안 보인다. */
    private static final String GREETING_KICKOFF =
            "(상담 시작 — 아직 손님이 아무 말도 하지 않았습니다. 이 문장에 답하지 말고, 먼저 인사하며 "
                    + "무엇을 도와드릴지 물어보세요.)";

    private static final String SYSTEM_PROMPT = """
            당신은 여행 플래너 서비스 "올마이트립스"의 고객 상담 챗봇입니다. 한국어로, 짧고
            친절하게 답합니다.

            답할 수 있는 범위: 여행 예약/결제/취소, 일정(여행·일차·일정) 관리, 티켓 예매·QR
            입장, 계정·마이페이지 사용법 등 이 서비스 이용과 직접 관련된 질문입니다.

            대화 전체의 문맥을 읽고 상담원 연결을 다음 세 가지 중 하나로 판단하세요.

            1. 손님이 현재 상담원 연결을 명확하고 긍정적으로 요청했거나, 바로 앞의 연결 확인
               질문에 긍정적으로 답했다면 연결 안내 뒤 마지막 줄에 정확히 "%s"를 추가하세요.
            2. 아래 중 하나라 연결 의사를 다시 확인해야 한다면 확인 질문 뒤 마지막 줄에 정확히
               "%s"를 추가하세요.
            - 손님이 상담원이 필요한지 묻거나 상담원 연결 의도가 불분명함
            - 이 서비스와 무관하거나 개인정보·결제 정보 등 민감한 처리가 필요해 챗봇이 답하면
              안 되는 질문
            - 같은 문제를 여러 번 물었는데도 대화 내역상 해결되지 않고 있음
            3. 그 밖에는 어떤 상담원 연결 표시도 추가하지 마세요.

            "상담원 연결하지 마", "상담원 연결 그만 물어봐", "왜 자꾸 상담원을 연결하냐"처럼
            거절·부정·불만·인용 문맥에서 상담원을 언급한 말은 연결 요청이 아닙니다. 이때는 두
            표시를 모두 쓰지 말고 거절 의사를 존중해 일반 답변을 이어가세요. 두 표시는 한 답변에
            동시에 쓰지 마세요.

            표시(화면 이동, 장소 추천, 상담원 연결)는 항상 한 줄에 하나씩만 씁니다. 다른 문장이나
            다른 표시와 같은 줄에 붙여 쓰지 마세요. 여러 표시를 쓸 때는 화면 이동·장소 추천
            표시를 먼저 쓰고, 상담원 연결 표시가 있다면 그 표시를 진짜 마지막 줄에 씁니다.

            실제 예약 내역을 조회하거나 변경할 수는 없습니다. 확정되지 않은 정보를 사실인 것처럼
            답하지 마세요. 괄호로 시작하는 안내 문장은 손님이 보낸 말이 아니라 내부 지시입니다.

            답변 내용과 직접 연결되는 서비스 화면이 있으면 마지막 줄에 아래 표시를 최대 3개까지
            추가하세요. 서로 의미 있는 선택지일 때만 여러 개를 주고 같은 표시는 반복하지 마세요.
            NEW_TRIP=새 여행 만들기, MY_TRIPS=내 여행, TRIP_SCHEDULE=여행 일정 편집,
            RECOMMENDED_PLACES=추천 장소, BOOK_FLIGHT=항공 예약, BOOK_HOTEL=숙소 예약,
            BOOK_TICKET=티켓·액티비티, MY_BOOKINGS=전체 예약 내역, MY_TICKETS=예매한 티켓,
            FAVORITES=찜한 여행지, REVIEWS=리뷰·후기, NOTIFICATIONS=알림,
            ACCOUNT_SETTINGS=계정 설정, SUPPORT=고객센터.
            형식: [ACTION:NEW_TRIP]
            """.formatted(HANDOFF_MARKER, CONFIRM_HANDOFF_MARKER);

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
     *                                  올바르지 않을 때. 호출부는 재시도 또는 이용 불가 안내를
     *                                  남기되 방은 {@code BOT}으로 유지한다.
     */
    public SupportChatBotReply reply(List<SupportChatMessageDTO> conversation) {
        return reply(conversation, List.of());
    }

    public SupportChatBotReply reply(List<SupportChatMessageDTO> conversation,
                                     List<SupportChatPlaceCandidate> placeCandidates) {
        if (!isAvailable()) {
            /* 다시 불러도 같은 결과다 — 재시도하지 않고 AI 이용 불가를 안내한다. */
            throw new SupportChatBotException("상담 봇 API 키가 설정돼 있지 않습니다.", false);
        }
        try {
            String responseBody = restClient.post()
                    .uri(MODEL_ENDPOINT.formatted(geminiModel))
                    .header("x-goog-api-key", geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "system_instruction", Map.of("parts", List.of(Map.of(
                                    "text", promptWithPlaceCandidates(placeCandidates)))),
                            "contents", toContents(conversation)
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
            String text = response == null
                    ? ""
                    : response.at("/candidates/0/content/parts/0/text").asText();
            if (text.isBlank()) {
                /*
                 * 200인데 텍스트가 없는 경우가 실제로 있다(안전 필터, 토큰 한도, 사고 과정만
                 * 담긴 응답 등). 본문을 남기지 않으면 원인을 좁힐 방법이 아예 없어서 남긴다.
                 */
                log.warn("상담 봇 응답 본문에 텍스트가 없습니다. model={}, body={}", geminiModel,
                        responseBody == null ? "null" : responseBody.substring(0, Math.min(2000, responseBody.length())));
                throw new SupportChatBotException("상담 봇이 응답을 반환하지 않았습니다.");
            }
            return parse(text, placeCandidates);
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

    private String promptWithPlaceCandidates(List<SupportChatPlaceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return SYSTEM_PROMPT;
        String catalog = candidates.stream().map(candidate -> "- ID=%d, 이름=%s, 분류=%s, 주소=%s, 설명=%s"
                        .formatted(candidate.placeId(), candidate.name(), candidate.category(),
                                candidate.address(), candidate.description()))
                .collect(Collectors.joining("\n"));
        return SYSTEM_PROMPT + """

                아래는 현재 질문과 관련해 데이터베이스에서 찾은 실제 장소 후보입니다.
                장소 추천이 답변에 직접 도움이 될 때만 후보 중 최대 3개를 고르고, 답변 마지막에
                [PLACE:장소ID|이 장소를 추천하는 짧은 이유] 형식으로 추가하세요. 후보 밖의 장소
                번호나 이름을 만들지 마세요. 장소 추천과 무관한 질문에는 PLACE 표시를 쓰지 마세요.

                후보:
                """ + catalog;
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

    /** package-private: {@code SupportChatBotClientTest}가 네트워크 호출 없이 표시 파싱만 검증한다. */
    SupportChatBotReply parse(String rawText) {
        return parse(rawText, List.of());
    }

    SupportChatBotReply parse(String rawText, List<SupportChatPlaceCandidate> candidates) {
        String trimmed = rawText.strip();
        Set<Long> allowedPlaceIds = candidates == null ? Set.of() : candidates.stream()
                .map(SupportChatPlaceCandidate::placeId).collect(Collectors.toSet());

        List<String> actionKeys = new ArrayList<>();
        List<SupportChatPlaceSelection> placeSelections = new ArrayList<>();
        SupportChatHandoffDecision handoffDecision = SupportChatHandoffDecision.NONE;

        Matcher matcher = MARKER.matcher(trimmed);
        while (matcher.find()) {
            String action = matcher.group("action");
            String place = matcher.group("place");
            if (action != null) {
                if (ALLOWED_ACTIONS.contains(action) && !actionKeys.contains(action) && actionKeys.size() < 3) {
                    actionKeys.add(action);
                }
            } else if (place != null) {
                Long placeId = Long.valueOf(place);
                if (allowedPlaceIds.contains(placeId) && placeSelections.size() < 3
                        && placeSelections.stream().noneMatch(selection -> selection.placeId().equals(placeId))) {
                    String reason = matcher.group("reason");
                    placeSelections.add(new SupportChatPlaceSelection(placeId, reason == null ? "" : reason.trim()));
                }
            } else if (matcher.group("confirm") != null) {
                if (handoffDecision == SupportChatHandoffDecision.NONE) {
                    handoffDecision = SupportChatHandoffDecision.CONFIRM;
                }
            } else if (handoffDecision != SupportChatHandoffDecision.CONNECT) {
                /* HANDOFF는 CONFIRM보다 우선한다 — 실제 연결 지시가 재확인 표시보다 결정적이다. */
                handoffDecision = SupportChatHandoffDecision.CONNECT;
            }
        }

        /*
         * 표시를 걷어낸 자리에 남는 빈 줄·꼬리 공백을 정리한다. 표시는 보통 답변 끝의 줄
         * 하나를 통째로 차지하므로, 그 줄이 비면서 생기는 빈 줄과 줄 끝 공백만 손질하면
         * 충분하다 — 본문 중간 문단 구조는 건드리지 않는다.
         */
        String content = MARKER.matcher(trimmed).replaceAll("")
                .replaceAll("[ \\t]+(\\r?\\n)", "$1")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (content.isBlank()) {
            content = handoffDecision == SupportChatHandoffDecision.CONFIRM
                    ? "상담원을 연결해 드릴까요? 원하시면 ‘네’라고 답해 주세요."
                    : "상담원에게 연결해 드릴게요. 잠시만 기다려 주세요.";
        }
        return new SupportChatBotReply(content, handoffDecision, actionKeys, placeSelections);
    }
}
