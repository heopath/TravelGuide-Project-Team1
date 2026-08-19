package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p><b>"다시 부를지"는 트리거 개수가 아니라 메시지 ID로 정한다.</b> 처음에는 "트리거가
 * 그사이 한 번 더 왔는가"를 표시로 남겨 재실행 여부를 정했지만, 이 판단은 두 방향 모두
 * 틀릴 수 있었다(heopath 3·4차 리뷰).
 * <ul>
 *   <li>트리거가 또 왔어도 그 메시지가 이미 직전 스냅샷에 포함돼 있었을 수 있다 —
 *   재실행하면 같은 내용에 답이 두 번 저장된다.</li>
 *   <li>반대로, 손님 메시지가 직전 스냅샷 이후·이번 답변 저장 이전에 커밋되면 실제 저장
 *   순서는 {@code USER1 → USER2 → BOT1}이 된다. "마지막 메시지가 손님인가"로 판단하면
 *   마지막이 {@code BOT1}이므로 재실행하지 않는데, {@code USER2}는 어떤 호출의 스냅샷에도
 *   포함된 적이 없어 영영 답을 못 받는다 — 메시지 ID 순서상 더 뒤에 저장된 봇 응답이
 *   그보다 앞선 손님 메시지를 "가린" 것뿐, 실제로 답한 것이 아니다.</li>
 * </ul>
 * 그래서 {@link #respond}는 자신이 Gemini에 넘긴 스냅샷의 <b>가장 최근 손님 메시지 ID</b>를
 * 기억해 두고, 답을 저장한 뒤 그 ID보다 큰 손님 메시지가 실제로 있는지 DB에서 다시 확인한다.
 * 있으면 그 메시지는 이번 호출에 포함되지 않았다는 뜻이므로 — 마지막 메시지가 무엇이든 —
 * 다시 돈다. 이미 만든 답은 그대로 두고 새 메시지에 대한 답만 추가로 만들므로 낭비도 없다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class SupportChatBotOrchestrator {

    private static final String GEMINI_FAILURE_MESSAGE =
            "죄송해요, 지금 답변을 준비하는 데 문제가 생겼어요. 상담원에게 연결해 드릴게요.";

    private final SupportChatService supportChatService;
    private final SupportChatBotClient supportChatBotClient;

    /** 지금 답을 만들고 있는 방들. 방 번호가 있으면 진행 중이라는 뜻이다. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(SupportChatBotTriggerEvent event) {
        Long roomId = event.roomId();
        if (!inFlight.add(roomId)) {
            /*
             * 이미 이 방을 처리 중이다. 이 트리거는 조용히 버려도 된다 — 지금 도는 실행이
             * respond()에서 저장 직후 DB를 다시 확인해 새 손님 메시지를 스스로 찾아내므로,
             * 이 트리거가 알리려던 내용도 결국 반영된다.
             */
            return;
        }
        try {
            boolean runAgain;
            do {
                runAgain = respond(roomId);
            } while (runAgain);
        } finally {
            inFlight.remove(roomId);
        }
    }

    /** @return 이번에 답한 스냅샷 이후 새로 온 손님 메시지가 있어 다시 돌아야 하면 {@code true}. */
    private boolean respond(Long roomId) {
        /*
         * Gemini를 부르기 전에 먼저 값싸게 한 번 거른다. 그 사이 관리자가 이미 가져갔다면
         * 굳이 API를 호출할 이유가 없다 — 최종 저장 직전에 다시 한번 잠그고 확인하는 것과는
         * 별개의, 비용을 아끼기 위한 사전 검사다.
         */
        if (!supportChatService.isStillBot(roomId)) return false;

        List<SupportChatMessageDTO> conversation = supportChatService.recentMessages(roomId);
        Long snapshotUserMessageId = latestUserMessageId(conversation);
        /* 대화는 있는데 손님 메시지가 하나도 없으면(봇 인사만 있고 아직 질문이 없음) 답할 게 없다. */
        if (!conversation.isEmpty() && snapshotUserMessageId == null) return false;

        try {
            SupportChatBotReply reply = supportChatBotClient.reply(conversation);
            if (reply.handoff()) {
                supportChatService.recordBotHandoff(roomId, reply.content());
                return false; /* 상담원 대기로 넘어갔다 — 봇이 더 돌 이유가 없다. */
            }
            supportChatService.recordBotReply(roomId, reply.content());
        } catch (SupportChatBotException exception) {
            log.warn("상담 봇 응답 생성에 실패해 상담원 대기로 넘깁니다. roomId={}", roomId, exception);
            supportChatService.recordBotHandoff(roomId, GEMINI_FAILURE_MESSAGE);
            return false;
        }

        long watermark = snapshotUserMessageId == null ? 0L : snapshotUserMessageId;
        return supportChatService.hasUserMessageAfter(roomId, watermark);
    }

    /** 대화에서 가장 나중에 온 손님(USER) 메시지 ID. 손님 메시지가 없으면 {@code null}. */
    private static Long latestUserMessageId(List<SupportChatMessageDTO> conversation) {
        Long latest = null;
        for (SupportChatMessageDTO message : conversation) {
            if ("USER".equals(message.getSenderType())) {
                latest = message.getSupportChatMessageId();
            }
        }
        return latest;
    }
}
