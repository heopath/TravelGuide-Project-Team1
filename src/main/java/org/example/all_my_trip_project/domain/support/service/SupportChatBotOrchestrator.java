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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(SupportChatBotTriggerEvent event) {
        Long roomId = event.roomId();
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
