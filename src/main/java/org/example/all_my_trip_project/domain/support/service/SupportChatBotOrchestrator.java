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
import java.util.Map;
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
 * 중복되거나 순서가 뒤바뀐다. 이미 이 방의 답을 만드는 중이면 새 요청은 "끝나고 한 번 더"
 * 표시만 남기고 빠진다 — 이어지는 실행이 그 사이 쌓인 메시지까지 포함한 대화 내역을 다시
 * 읽으므로 늦게 온 질문이 묻히지 않는다. 지금은 단일 인스턴스 전제라(내장 심플 브로커와 같은
 * 전제) 프로세스 안의 표시로 충분하다.
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

    /**
     * 지금 답을 만들고 있는 방들. 값은 "끝나면 한 번 더 돌아야 한다"는 표시다.
     *
     * <p>키가 있으면 진행 중이라는 뜻이라 {@code Boolean.FALSE}도 의미가 있다 — 그래서
     * {@code Set}이 아니라 {@code Map}이다.
     */
    private final Map<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(SupportChatBotTriggerEvent event) {
        Long roomId = event.roomId();
        if (inFlight.putIfAbsent(roomId, Boolean.FALSE) != null) {
            /* 이미 이 방을 처리 중이다. 재실행 표시만 남기고 빠진다 — 겹쳐 부르지 않는다. */
            inFlight.put(roomId, Boolean.TRUE);
            return;
        }
        try {
            /*
             * 도는 동안 새 요청이 들어왔으면(표시가 TRUE라 remove가 실패하면) 한 번 더 돈다.
             * 다음 회차는 recentMessages를 다시 읽으므로 그 사이 온 질문까지 함께 본다.
             */
            do {
                inFlight.put(roomId, Boolean.FALSE);
                respond(roomId);
            } while (!inFlight.remove(roomId, Boolean.FALSE));
        } finally {
            /* 예외로 빠져나가도 방이 잠긴 채 남지 않게 한다. */
            inFlight.remove(roomId);
        }
    }

    private void respond(Long roomId) {
        /*
         * Gemini를 부르기 전에 먼저 값싸게 한 번 거른다. 그 사이 관리자가 이미 가져갔다면
         * 굳이 API를 호출할 이유가 없다 — 최종 저장 직전에 다시 한번 잠그고 확인하는 것과는
         * 별개의, 비용을 아끼기 위한 사전 검사다.
         */
        if (!supportChatService.isStillBot(roomId)) return;

        List<SupportChatMessageDTO> conversation = supportChatService.recentMessages(roomId);
        try {
            SupportChatBotReply reply = supportChatBotClient.reply(conversation);
            if (reply.handoff()) {
                supportChatService.recordBotHandoff(roomId, reply.content());
            } else {
                supportChatService.recordBotReply(roomId, reply.content());
            }
        } catch (SupportChatBotException exception) {
            log.warn("상담 봇 응답 생성에 실패해 상담원 대기로 넘깁니다. roomId={}", roomId, exception);
            supportChatService.recordBotHandoff(roomId, GEMINI_FAILURE_MESSAGE);
        }
    }
}
