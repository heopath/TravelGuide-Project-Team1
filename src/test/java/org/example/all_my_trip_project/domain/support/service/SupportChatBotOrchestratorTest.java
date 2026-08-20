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
     * 재실행 여부는 트리거가 왔는지가 아니라, 내가 이미 어디까지 답했는지(워터마크)로 정한다
     * (heopath 3·4차 리뷰). 두 번째 트리거가 왔더라도 그 메시지가 이미 첫 스냅샷에 포함돼
     * 있었다면(= 워터마크 이후 새 메시지가 없다면) 다시 Gemini를 부르면 안 된다 — 같은
     * 질문에 답이 두 번 저장된다.
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
        /* 대화 내용은 끝까지 USER1뿐이다 — 재실행 회차가 다시 읽어도 이미 답한 지점(워터마크=1)
           보다 새로운 메시지가 없으므로 Gemini를 또 부르면 안 된다. */
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

        /* 두 번째 트리거가 온다 — 이미 처리 중이므로 표시만 남고 겹쳐 부르지는 않는다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        assertThat(replyCalls.get()).isEqualTo(1);

        release.countDown();
        first.join(3000);

        assertThat(replyCalls.get()).isEqualTo(1); /* Gemini는 한 번만 불렸다 */
        assertThat(peak.get()).isEqualTo(1);        /* 같은 방에 동시 호출은 없었다 */
        verify(service, times(1)).recordBotReply(eq(ROOM_ID), any());
    }

    /*
     * heopath 4차 리뷰가 지적한 정확한 시나리오: 첫 호출이 USER1까지만 읽고 Gemini를 부르는
     * 동안 USER2가 커밋되지만, 답(BOT1)은 그보다 늦게 저장된다. 그래서 실제 저장 순서는
     * USER1 → USER2 → BOT1이 되어 "마지막 메시지가 손님인가"로는 판단할 수 없다 — 마지막은
     * BOT1이다. 워터마크(스냅샷의 마지막 USER ID)와 비교해야 USER2를 놓치지 않는다.
     */
    @Test
    @DisplayName("첫 스냅샷 처리 중에 온 사용자 메시지는 봇 답변 뒤에 저장돼도 놓치지 않는다")
    void answersUserMessageThatArrivesBeforeFirstReplyIsSaved() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger replyCalls = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        SupportChatMessageDTO user1 = message(1L, "USER");
        SupportChatMessageDTO user2 = message(2L, "USER");
        SupportChatMessageDTO bot1 = message(3L, "BOT");
        /* 1회차: USER1까지만 보인다. 2회차 이후: 실제 저장 순서 USER1 → USER2 → BOT1을 그대로 재현. */
        when(service.recentMessages(ROOM_ID))
                .thenReturn(List.of(user1))
                .thenReturn(List.of(user1, user2, bot1));

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

        /* 이 시점에 실제로는 USER2가 이미 커밋됐다 — 두 번째 트리거가 발행되지만 이미 처리 중이라 버려진다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));
        assertThat(replyCalls.get()).isEqualTo(1);

        release.countDown();
        first.join(3000);

        assertThat(replyCalls.get()).isEqualTo(2); /* USER1에 한 번, USER2에 한 번 */
        assertThat(peak.get()).isEqualTo(1);        /* 같은 방에 동시 호출은 없었다 */
        verify(service, times(2)).recordBotReply(eq(ROOM_ID), any());
    }

    /*
     * heopath 5차 리뷰: "더 답할 게 없다"고 판단한 시점과 onTrigger()가 실제로 inFlight에서
     * 방을 지우는 시점 사이에도 틈이 있다. 그 틈에 새 트리거가 오면 inFlight에 방이 아직
     * 남아 있어 트리거는 버려지는데, 지금 실행이 그 판단을 그대로 밀어붙여 잠금을 지우면 그
     * 메시지는 아무도 처리하지 않는다. "더 있는지 확인하는" 두 번째 recentMessages() 호출
     * 안에서 멈춰 그 순간(확인 중이지만 아직 잠금은 안 놓은 상태)을 재현한다.
     */
    @Test
    @DisplayName("답할 게 없다고 확인하는 도중, 잠금을 놓기 전에 온 메시지를 놓치지 않는다")
    void answersUserMessageThatArrivesRightBeforeLockIsReleased() throws Exception {
        CountDownLatch checking = new CountDownLatch(1);
        CountDownLatch releaseCheck = new CountDownLatch(1);
        AtomicInteger replyCalls = new AtomicInteger();
        AtomicInteger geminiInFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger recentMessagesCalls = new AtomicInteger();

        SupportChatMessageDTO user1 = message(1L, "USER");
        SupportChatMessageDTO user2 = message(2L, "USER");

        when(service.recentMessages(ROOM_ID)).thenAnswer(invocation -> {
            int call = recentMessagesCalls.incrementAndGet();
            if (call == 1) {
                return List.of(user1); /* 1회차: USER1에 답한다. */
            }
            if (call == 2) {
                /* 2회차: "더 있는지" 확인하는 조회 — 이 순간에 멈춰서 잠금 해제 직전의
                   틈을 재현한다. 이 조회 자체는 아직 USER2를 못 본 것으로 둔다. */
                checking.countDown();
                releaseCheck.await(2, TimeUnit.SECONDS);
                return List.of(user1);
            }
            return List.of(user1, user2); /* 놓친 트리거를 되찾아 다시 확인하는 회차. */
        });

        when(client.reply(any())).thenAnswer(invocation -> {
            peak.accumulateAndGet(geminiInFlight.incrementAndGet(), Math::max);
            try {
                int call = replyCalls.incrementAndGet();
                return new SupportChatBotReply("답변 " + call, false);
            } finally {
                geminiInFlight.decrementAndGet();
            }
        });

        Thread first = new Thread(() -> orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID)));
        first.start();
        assertThat(checking.await(2, TimeUnit.SECONDS)).isTrue();

        /* 확인은 진행 중이지만 잠금은 아직 안 놓인 시점 — 이 사이에 USER2가 이미 커밋됐다고 가정한다. */
        orchestrator.onTrigger(new SupportChatBotTriggerEvent(ROOM_ID));

        releaseCheck.countDown();
        first.join(3000);

        assertThat(replyCalls.get()).isEqualTo(2); /* USER1에 한 번, 놓칠 뻔한 USER2에 한 번 */
        assertThat(peak.get()).isEqualTo(1);         /* 같은 방에 동시 호출은 없었다 */
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
