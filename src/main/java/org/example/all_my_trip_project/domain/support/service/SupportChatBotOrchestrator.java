package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SupportChatBotTriggerEvent}를 받아 Gemini를 부르고 결과를 반영한다.
 *
 * <p>{@link SupportChatService}와 다른 빈으로 분리한 이유는 두 가지다.
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}가 트랜잭션이 커밋된 뒤에만 불려야
 *   하므로, 메시지를 저장한 트랜잭션과 이 처리는 서로 다른 트랜잭션이어야 한다.</li>
 *   <li>{@code @Async}로 별도 스레드에서 돌아야 Gemini 응답을 기다리는 동안 요청 스레드를
 *   막지 않는다(설계 문서 §5 — "Gemini 호출은 트랜잭션 밖에서, 비동기로").</li>
 * </ul>
 * 같은 클래스 안에서 이런 메서드를 호출하면(자기 자신 호출) 스프링 프록시가 끼어들지 못해
 * {@code @Async}/{@code @Transactional}이 조용히 무시된다 — 그래서 별도 빈으로 뒀다.
 *
 * <p><b>한 방에서는 한 번에 하나만 부른다.</b> 손님이 여러 탭을 열어 두거나 대기 표시가
 * 복원되지 않은 창에서 연달아 보내면 같은 방에 Gemini 호출이 겹칠 수 있고, 그러면 답이
 * 중복되거나 순서가 뒤바뀐다. 지금은 단일 인스턴스 전제라(내장 심플 브로커와 같은 전제)
 * 방 번호를 담은 {@link #inFlight}만으로 충분하다.
 *
 * <p><b>"다시 부를지"는 트리거 개수가 아니라, 내가 이미 어디까지 답했는지로 정한다.</b>
 * 트리거가 왔는지(개수)로 판단하면 두 방향 모두 틀릴 수 있었다(heopath 3·4차 리뷰).
 * <ul>
 *   <li>트리거가 또 왔어도 그 메시지가 이미 직전 스냅샷에 포함돼 있었을 수 있다 —
 *   재실행하면 같은 내용에 답이 두 번 저장된다.</li>
 *   <li>반대로, 손님 메시지가 직전 스냅샷 이후·이번 답변 저장 이전에 커밋되면 실제 저장
 *   순서는 {@code USER1 → USER2 → BOT1}이 된다. "마지막 메시지가 손님인가"로 판단하면
 *   마지막이 {@code BOT1}이므로 재실행하지 않는데, {@code USER2}는 어떤 호출의 스냅샷에도
 *   포함된 적이 없어 영영 답을 못 받는다 — 메시지 ID 순서상 더 뒤에 저장된 봇 응답이
 *   그보다 앞선 손님 메시지를 "가린" 것뿐, 실제로 답한 것이 아니다.</li>
 * </ul>
 * 그래서 {@link #onTrigger}는 지금까지 답한 <b>가장 최근 손님 메시지 ID(워터마크)</b>를
 * 스레드 로컬 변수로 들고, {@link #respond}를 부를 때마다 넘긴다. {@code respond}는 DB를
 * 새로 읽어 그 워터마크보다 큰 손님 메시지가 있을 때만 Gemini를 부르고, 답을 저장한 뒤
 * 그 메시지의 ID를 새 워터마크로 돌려준다. 워터마크가 실제로 늘었으면(=방금 뭔가 답했으면)
 * 곧바로 한 번 더 확인한다 — 그 사이 또 새 메시지가 왔을 수 있기 때문이다. 워터마크가
 * 그대로면(=이번 스냅샷에는 이미 답한 것 이상이 없으면) 더 부를 필요가 없다.
 *
 * <p><b>"더 없다"고 확인한 순간과 잠금을 실제로 놓는 순간 사이에도 틈이 있다.</b> 그 짧은
 * 순간에 새 트리거가 도착하면 {@link #inFlight}에 방이 여전히 남아 있어 트리거는 버려지는데,
 * 지금 실행이 그 판단을 그대로 밀어붙여 잠금을 지우면 그 메시지는 아무도 처리하지 않는다
 * (heopath 5차 리뷰). 그래서 잠금을 지우는 연산 자체를, "그 순간까지 아무도 트리거를 놓치지
 * 않았을 때만" 성립하는 원자적 조건부 삭제로 만든다 — 확인과 해제를 하나의 연산으로 묶어야
 * 그 사이의 어떤 순간에 트리거가 와도 안전하다. 놓친 트리거가 있었다면 {@link #respond}를
 * 한 번 더 부르는데, 이때도 워터마크 비교로 판단하므로 실제로 새 메시지가 없었다면(트리거만
 * 왔을 뿐 내용은 이미 반영돼 있었다면) Gemini를 다시 부르지 않는다.
 *
 * <p><b>소유권을 얻는 쪽도 "확인"과 "표시 남기기"가 따로면 반대 방향으로 틀린다.</b>
 * {@code putIfAbsent()}로 기존 실행이 있는지 확인한 다음 별도로 {@code put(TRUE)}를 하면, 그
 * 사이(확인은 했지만 표시는 아직 안 남긴 순간) 기존 실행이 "더 없다"고 판단해 조건부 삭제로
 * 먼저 빠져나갈 수 있다. 그러면 뒤늦은 {@code put(TRUE)}가 map에 <b>주인 없는 표시</b>를
 * 남긴다 — 그 방을 처리하는 실행은 이제 아무도 없는데, map에는 여전히 값이 남아 있어 이후
 * 모든 트리거가 "이미 처리 중"으로 오판하고 조용히 버려진다(heopath 6차 리뷰). 그래서 "이미
 * 처리 중인 실행이 있는지 확인"과 "없으면 내가 소유자가 되고, 있으면 표시만 남기기"를
 * {@link Map#compute}로 하나의 원자적 연산으로 묶는다 — 확인과 그에 따른 조치 사이에 어떤
 * 틈도 남기지 않아야, 기존 실행이 그 순간 막 잠금을 지우더라도 정확히 둘 중 하나로만
 * 귀결된다: 내가 새 소유자가 되어 직접 처리하거나, 기존 실행에게 표시가 안전하게 전달된다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class SupportChatBotOrchestrator {


    /**
     * 일시적 실패에 남기는 안내. 방은 {@code BOT}에 그대로 두고 손님이 다시 물어볼 수 있게 한다.
     *
     * <p>이 문구가 직전 봇 발화와 같으면 "연속 실패"로 보고 AI 이용 불가를 안내한다
     * ({@link #lastBotMessageIsRetryNotice}). 별도 카운터를 두지 않고 대화 내역으로 판단하므로
     * 서버가 재시작해도 판단이 유지된다.
     */
    private static final String GEMINI_RETRY_MESSAGE =
            "죄송해요, 지금 답변을 준비하지 못했어요. 잠시 후 다시 물어봐 주시겠어요?";
    private static final String GEMINI_UNAVAILABLE_MESSAGE =
            "현재 AI 상담을 이용할 수 없어요. 잠시 후 다시 이용해 주세요.";

    /** 아직 어떤 손님 메시지에도 답하지 않은 상태를 나타내는 워터마크. 실제 메시지 ID는 1부터 시작한다. */
    private static final long NOT_ANSWERED_YET = -1L;

    private final SupportChatService supportChatService;
    private final SupportChatBotClient supportChatBotClient;
    private final SupportChatActionPersonalizer actionPersonalizer;
    private final SupportChatPlaceRecommendationService placeRecommendationService;

    /**
     * 지금 답을 만들고 있는 방들. 방 번호가 있으면 진행 중이라는 뜻이다.
     *
     * <p>값은 "내가 확인을 마치고 잠금을 놓으려는 사이에 트리거를 하나 놓쳤다"는 표시다.
     * {@code respond()}가 더 답할 게 없다고 판단해도, 그 판단과 잠금 해제 사이에 새 트리거가
     * 오면 이 값을 {@code TRUE}로 바꿔 둔다. 잠금을 놓는 쪽은 이 값이 여전히 {@code FALSE}일
     * 때만(=그 사이 아무도 트리거를 놓치지 않았을 때만) 원자적으로 지운다.
     */
    private final Map<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(SupportChatBotTriggerEvent event) {
        Long roomId = event.roomId();
        if (!acquireOrMarkMissed(roomId)) {
            /* 이미 이 방을 처리하는 실행이 있다 — 표시만 안전하게 남기고 빠진다. 그 실행이
               이 순간 막 종료됐다면 acquireOrMarkMissed()가 대신 내게 소유권을 준다. */
            return;
        }
        try {
            long answeredUpTo = NOT_ANSWERED_YET;
            while (true) {
                long updated = respond(roomId, answeredUpTo);
                if (updated > answeredUpTo) {
                    answeredUpTo = updated;
                    continue; /* 방금 답했다 — 그 사이 더 오지 않았는지 곧바로 한 번 더 본다. */
                }
                if (inFlight.remove(roomId, Boolean.FALSE)) {
                    return; /* 확인부터 지금까지 표시가 FALSE였다 — 안전하게 종료한다. */
                }
                /* 그 사이 표시가 TRUE로 바뀌어 있었다 — 놓칠 뻔한 트리거가 있었다는 뜻이다.
                   표시를 내리고 DB를 다시 확인한다(내용이 실제로 새로울 때만 다시 답한다). */
                inFlight.put(roomId, Boolean.FALSE);
            }
        } catch (RuntimeException exception) {
            /* 예외로 빠져나가도 방이 잠긴 채 남지 않게 한다. */
            inFlight.remove(roomId);
            throw exception;
        }
    }

    /**
     * 이 방을 처리할 소유권을 얻거나, 이미 처리 중인 실행에게 표시만 남긴다.
     *
     * <p>"이미 처리 중인지 확인"과 그에 따른 조치(소유권 획득 또는 표시 남기기)를
     * {@link Map#compute}로 하나의 원자적 연산으로 묶는다. 따로 하면(확인 → 그 사이 기존
     * 실행이 조건부 삭제로 먼저 끝남 → 뒤늦게 표시만 남김) map에 주인 없는 표시가 남아
     * 이후 트리거를 전부 흡수해 버린다(heopath 6차 리뷰).
     *
     * @return 이 호출이 소유권을 얻었으면 {@code true}.
     */
    private boolean acquireOrMarkMissed(Long roomId) {
        AtomicBoolean acquired = new AtomicBoolean(false);
        inFlight.compute(roomId, (id, current) -> {
            if (current == null) {
                acquired.set(true);
                return Boolean.FALSE; /* 아무도 처리 중이 아니었다 — 내가 소유자가 된다. */
            }
            return Boolean.TRUE; /* 이미 처리 중이다 — 놓칠 뻔한 트리거 표시만 남긴다. */
        });
        return acquired.get();
    }

    /**
     * 워터마크({@code answeredUpTo})보다 새로운 손님 메시지가 있을 때만 Gemini를 부른다.
     *
     * @return 이번 호출이 답한 손님 메시지 ID(새로 답한 게 없으면 {@code answeredUpTo} 그대로).
     */
    private long respond(Long roomId, long answeredUpTo) {
        /*
         * Gemini를 부르기 전에 먼저 값싸게 한 번 거른다. 그 사이 관리자가 이미 가져갔다면
         * 굳이 API를 호출할 이유가 없다 — 최종 저장 직전에 다시 한번 잠그고 확인하는 것과는
         * 별개의, 비용을 아끼기 위한 사전 검사다.
         */
        if (!supportChatService.isStillBot(roomId)) return answeredUpTo;

        List<SupportChatMessageDTO> conversation = supportChatService.recentMessages(roomId);
        Long latestUserMessageId = latestUserMessageId(conversation);
        if (latestUserMessageId == null) {
            /* 손님 메시지가 아예 없다. 대화가 비어 있으면 첫 인사, 봇 인사만 있으면 답할 게 없다. */
            if (!conversation.isEmpty()) return answeredUpTo;
            latestUserMessageId = 0L;
        }
        if (latestUserMessageId <= answeredUpTo) {
            return answeredUpTo; /* 이미 이 지점까지 답했다 — 다시 부를 필요 없다. */
        }

        /*
         * "봇 답변이 느리다"는 피드백이 백엔드↔프론트 구간 문제인지, 백엔드↔제미나이 호출
         * 자체가 느린 것인지 구분할 방법이 없어 Gemini 호출 구간만 따로 재서 남겼었다. 그런데
         * 그 구간 하나로 뭉치면 오해하기 쉽다 — RAG가 켜진 프로필(local-ai/prod-ai-rag)에서는
         * 장소와 무관한 질문에도 매번 candidates()가 먼저 Cohere 임베딩 API를 호출해 후보를
         * 찾고, Gemini 호출이 끝난 뒤에는 개인화(TripService 조회)·장소 카드 DB 조회가 이어진다.
         * 이 셋을 하나로 재면 "Gemini가 느리다"는 결론이 실제로는 RAG 검색이나 후처리 DB 조회
         * 때문일 수도 있다는 걸 가려버린다. 그래서 구간별로 따로 잰다 — 나머지(DB 읽기/쓰기,
         * WebSocket 전송)는 보통 수십 ms 이내다.
         */
        Instant respondStartedAt = Instant.now();
        try {
            String latestQuestion = latestUserContent(conversation);

            Instant ragStartedAt = Instant.now();
            List<SupportChatPlaceCandidate> placeCandidates = placeRecommendationService.candidates(latestQuestion);
            long ragElapsedMs = Duration.between(ragStartedAt, Instant.now()).toMillis();

            Instant geminiStartedAt = Instant.now();
            SupportChatBotReply reply = placeCandidates.isEmpty()
                    ? supportChatBotClient.reply(conversation)
                    : supportChatBotClient.reply(conversation, placeCandidates);
            long geminiElapsedMs = Duration.between(geminiStartedAt, Instant.now()).toMillis();

            Instant postProcessStartedAt = Instant.now();
            List<String> personalizedActions = actionPersonalizer.personalize(
                    roomId, conversation, reply.actionKeys());
            List<Map<String, Object>> placeCards = placeRecommendationService.cards(
                    reply.placeSelections(), placeCandidates);
            long postProcessElapsedMs = Duration.between(postProcessStartedAt, Instant.now()).toMillis();

            log.info("상담 봇 응답 생성 소요 시간: roomId={}, ragMs={}, geminiMs={}, postProcessMs={}, totalMs={}",
                    roomId, ragElapsedMs, geminiElapsedMs, postProcessElapsedMs,
                    Duration.between(respondStartedAt, Instant.now()).toMillis());
            if (reply.handoffDecision() == SupportChatHandoffDecision.CONNECT) {
                supportChatService.recordBotHandoff(roomId, reply.content(), personalizedActions);
            } else if (reply.handoffDecision() == SupportChatHandoffDecision.CONFIRM) {
                supportChatService.recordBotReply(
                        roomId, SupportChatService.HUMAN_CONFIRMATION_REPLY, personalizedActions);
            } else {
                if (placeCards.isEmpty()) {
                    supportChatService.recordBotReply(roomId, reply.content(), personalizedActions);
                } else {
                    supportChatService.recordBotReply(roomId, reply.content(), personalizedActions, placeCards);
                }
            }
        } catch (SupportChatBotException exception) {
            long failedAfterMs = Duration.between(respondStartedAt, Instant.now()).toMillis();
            /*
             * 일시적 실패를 상담원 대기로 넘기지 않는다. 복구 가능한 오류마다 WAITING으로
             * 넘기면 손님이 직접 봇 복귀나 새 상담을 선택해야 하므로, 방을 BOT에 둔 채 바로
             * 다시 물어볼 수 있게 한다.
             *
             * AI가 실패한 상황에서는 문맥을 판단할 수 없으므로 상담원 연결을 추측하지 않는다.
             * 연속 실패나 재시도 불가능 오류에는 이용 불가 안내만 남기고 방은 BOT으로 유지한다.
             */
            if (exception.isRetryable() && !lastBotMessageIsRetryNotice(conversation)) {
                log.warn("상담 봇 응답 생성에 실패해 재시도 안내를 남깁니다(방은 BOT 유지). roomId={}, 실패까지 ms={}",
                        roomId, failedAfterMs, exception);
                supportChatService.recordBotReply(roomId, GEMINI_RETRY_MESSAGE);
            } else {
                log.warn("상담 봇 응답 생성에 반복 실패해 이용 불가 안내를 남깁니다. roomId={}, 실패까지 ms={}, 재시도가능={}",
                        roomId, failedAfterMs, exception.isRetryable(), exception);
                supportChatService.recordBotReply(roomId, GEMINI_UNAVAILABLE_MESSAGE);
            }
        } catch (RuntimeException exception) {
            /*
             * RAG 후보 검색·개인화·장소 카드 조회는 SupportChatBotClient 호출과 달리
             * SupportChatBotException을 쓰지 않는다. 이 구간에서 예상 못 한 오류가 나면
             * 위 catch가 못 잡아 손님 메시지만 저장된 채 방이 조용히 BOT에 멈춰 있었다
             * (kilo-code-bot PR #407 리뷰 지적). 원인을 알 수 없는 오류라 재시도 여부를
             * 섣불리 판단하지 않고, 우선 이용 불가 안내로 손님이 다시 시도해 볼 수 있게 한다.
             */
            long failedAfterMs = Duration.between(respondStartedAt, Instant.now()).toMillis();
            log.error("상담 봇 응답 생성 중 예상하지 못한 오류가 발생해 이용 불가 안내를 남깁니다. roomId={}, 실패까지 ms={}",
                    roomId, failedAfterMs, exception);
            supportChatService.recordBotReply(roomId, GEMINI_UNAVAILABLE_MESSAGE);
        }
        return latestUserMessageId;
    }

    /** 대화에서 가장 나중에 온 손님(USER) 메시지 ID. 손님 메시지가 없으면 {@code null}. */
    /**
     * 직전 봇 발화가 이미 재시도 안내였는지 본다 — 즉 이번이 연속 두 번째 실패인지.
     *
     * <p>손님 메시지는 건너뛰고 가장 최근 {@code BOT} 발화 하나만 본다. 실패 → 손님이 다시 물음
     * → 또 실패인 흐름에서 중간의 손님 메시지 때문에 판단이 틀어지지 않게 하기 위해서다.
     */
    private static boolean lastBotMessageIsRetryNotice(List<SupportChatMessageDTO> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            SupportChatMessageDTO message = conversation.get(i);
            if (!"BOT".equals(message.getSenderType())) continue;
            return GEMINI_RETRY_MESSAGE.equals(message.getContent());
        }
        return false;
    }

    private static Long latestUserMessageId(List<SupportChatMessageDTO> conversation) {
        Long latest = null;
        for (SupportChatMessageDTO message : conversation) {
            if ("USER".equals(message.getSenderType())) {
                latest = message.getSupportChatMessageId();
            }
        }
        return latest;
    }

    private static String latestUserContent(List<SupportChatMessageDTO> conversation) {
        for (int index = conversation.size() - 1; index >= 0; index--) {
            SupportChatMessageDTO message = conversation.get(index);
            if ("USER".equals(message.getSenderType())) return message.getContent();
        }
        return "";
    }
}
