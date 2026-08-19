package org.example.all_my_trip_project.domain.support.service;

import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 한 방에서 봇 응답 생성이 겹치지 않는지 본다.
 *
 * <p>손님이 여러 탭을 열어 두거나 대기 표시가 복원되지 않은 창에서 연달아 보내면 같은 방에
 * Gemini 호출이 동시에 뜰 수 있다. 그러면 답변이 중복되거나 순서가 뒤바뀐다.
 */
class SupportChatBotOrchestratorTest {

    private static final Long ROOM_ID = 5L;

    private SupportChatService service;
    private SupportChatBotClient client;
    private SupportChatBotOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        service = mock(SupportChatService.class);
        client = mock(SupportChatBotClient.class);
        orchestrator = new SupportChatBotOrchestrator(service, client);
        when(service.isStillBot(ROOM_ID)).thenReturn(true);
        when(service.recentMessages(ROOM_ID)).thenReturn(List.of());
    }

    private static SupportChatMessageDTO message(long id, String senderType) {
        return SupportChatMessageDTO.builder()
                .supportChatMessageId(id).supportChatRoomId(ROOM_ID)
                .senderType(senderType).content("내용").build();
    }

    /*
     * 재실행 표시는 "그사이 트리거가 한 번 더 왔다"만 알 뿐, 그 트리거의 메시지가 이미 직전
     * 호출의 대화 스냅샷에 포함됐는지는 모른다(heopath 3차 리뷰). 두 번째 트리거가 도착했을
     * 때 이미 그 메시지까지 반영된 상태라면(마지막 메시지가 이미 BOT), 재실행 표시가 남아
     * 있어도 다시 Gemini를 부르면 안 된다 — 같은 질문에 답이 두 번 저장된다.
     */
    @Test
    @DisplayName("두 번째 트리거의 메시지가 첫 스냅샷에 이미 포함됐으면 답은 한 번만 저장된다")
    void doesNotAnswerTwiceWhenSecondTriggerAlreadyCoveredByFirstSnapshot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger replyCalls = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        SupportChatMessageDTO question = message(1L, "USER");
        SupportChatMessageDTO answer = message(2L, "BOT");
        when(service.recentMessages(ROOM_ID)).thenReturn(List.of(question));

        when(client.reply(any())).thenAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                replyCalls.incrementAndGet();
                entered.countDown();
                release.await(2, TimeUnit.SECONDS);
                return new SupportChatBotReply("무엇을 도와드릴까요?", false);
            } finally {
                inFlight.decrementAndGet();
            }
        });

        Thread first = new Thread(() -> orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID)));
        first.start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        /* 두 번째 트리거가 온다 — 재실행 표시만 남고 겹쳐 부르지는 않는다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        assertThat(replyCalls.get()).isEqualTo(1);

        /*
         * 그 사이 실제로는 이미 답이 저장된 상태였다고 가정한다(첫 조회가 질문까지 포함해
         * 답을 준비 중이었을 뿐). 재실행 회차가 다시 조회하면 이미 BOT이 마지막이다.
         */
        when(service.recentMessages(ROOM_ID)).thenReturn(List.of(question, answer));

        release.countDown();
        first.join(3000);

        assertThat(replyCalls.get()).isEqualTo(1); /* Gemini는 한 번만 불렸다 */
        assertThat(peak.get()).isEqualTo(1);        /* 같은 방에 동시 호출은 없었다 */
        verify(service, times(1)).recordBotReply(eq(ROOM_ID), any());
    }

    /* 첫 스냅샷 이후에 손님이 정말로 새 질문을 보냈다면, 재실행 회차가 그 질문에 답해야 한다. */
    @Test
    @DisplayName("첫 스냅샷 이후 새 사용자 메시지가 도착했으면 한 번 더 답한다")
    void answersAgainWhenNewUserMessageArrivesAfterFirstSnapshot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger replyCalls = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        SupportChatMessageDTO firstQuestion = message(1L, "USER");
        SupportChatMessageDTO firstAnswer = message(2L, "BOT");
        SupportChatMessageDTO secondQuestion = message(3L, "USER");
        when(service.recentMessages(ROOM_ID)).thenReturn(List.of(firstQuestion));

        when(client.reply(any())).thenAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                int call = replyCalls.incrementAndGet();
                if (call == 1) {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                }
                return new SupportChatBotReply("답변 " + call, false);
            } finally {
                inFlight.decrementAndGet();
            }
        });

        Thread first = new Thread(() -> orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID)));
        first.start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        /* 아직 첫 호출이 돌고 있다. 이 요청은 겹쳐 부르지 않고 재실행 표시만 남기고 빠진다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        assertThat(replyCalls.get()).isEqualTo(1);

        /* 첫 답변이 저장된 뒤, 손님이 진짜 새 질문을 보냈다고 가정한다. */
        when(service.recentMessages(ROOM_ID)).thenReturn(List.of(firstQuestion, firstAnswer, secondQuestion));

        release.countDown();
        first.join(3000);

        assertThat(replyCalls.get()).isEqualTo(2); /* 처음 한 번 + 새 질문에 답하는 재실행 한 번 */
        assertThat(peak.get()).isEqualTo(1);        /* 같은 방에 동시 호출은 없었다 */
        verify(service, times(2)).recordBotReply(eq(ROOM_ID), any());
    }

    @Test
    @DisplayName("응답이 끝나면 다음 요청은 다시 정상적으로 처리된다")
    void acceptsNewTriggerAfterCompletion() {
        when(client.reply(any())).thenReturn(new SupportChatBotReply("도와드릴게요.", false));

        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));

        verify(service, times(2)).recordBotReply(eq(ROOM_ID), any());
    }

    /* 예외로 빠져나가도 방이 잠긴 채 남으면, 그 뒤로 그 방은 영영 답을 못 받는다. */
    @Test
    @DisplayName("호출이 예외로 끝나도 다음 요청은 막히지 않는다")
    void releasesRoomWhenReplyThrows() {
        when(client.reply(any())).thenThrow(new SupportChatBotException("Gemini 호출 실패"));

        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));

        verify(service, times(2)).recordBotHandoff(eq(ROOM_ID), any());
    }
}
