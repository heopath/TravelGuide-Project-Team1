package org.example.all_my_trip_project.domain.support.service;

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

    /*
     * 늦게 온 요청을 그냥 버리면 그 질문은 답을 못 받는다. 겹쳐 부르지 않되, 돌던 것이 끝난 뒤
     * 한 번 더 돌아 그 사이 쌓인 메시지까지 함께 본다.
     */
    @Test
    @DisplayName("응답 생성 중에 온 요청은 겹쳐 부르지 않고 끝난 뒤 한 번만 다시 돈다")
    void coalescesTriggersWhileReplyInFlight() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        when(client.reply(any())).thenAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                if (calls.incrementAndGet() == 1) {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                }
                return new SupportChatBotReply("무엇을 도와드릴까요?", false);
            } finally {
                inFlight.decrementAndGet();
            }
        });

        Thread first = new Thread(() -> orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID)));
        first.start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        /* 아직 첫 호출이 돌고 있다. 이 두 건은 겹쳐 부르지 않고 표시만 남기고 빠져야 한다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        assertThat(calls.get()).isEqualTo(1);

        release.countDown();
        first.join(3000);

        assertThat(calls.get()).isEqualTo(2); /* 처음 한 번 + 밀린 요청을 합친 재실행 한 번 */
        assertThat(peak.get()).isEqualTo(1);  /* 같은 방에 동시 호출은 없었다 */
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
