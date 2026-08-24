package org.example.all_my_trip_project.domain.support.service;

import java.util.List;

/**
 * 봇의 답변 한 건.
 *
 * @param content  손님에게 보여줄 내용. {@code [HANDOFF]} 표시는 이미 걷어낸 상태다.
 * @param handoff  true면 방을 {@code WAITING}으로 넘긴다(상담원 요청 / 정책 범위 밖 / 반복 미해결).
 * @param actionKeys 답변과 함께 보여줄 내부 화면 이동 액션. 표시 순서대로 최대 3개다.
 */
public record SupportChatBotReply(String content, boolean handoff, List<String> actionKeys) {
    public SupportChatBotReply(String content, boolean handoff) {
        this(content, handoff, List.of());
    }

    public SupportChatBotReply(String content, boolean handoff, String actionKey) {
        this(content, handoff, actionKey == null ? List.of() : List.of(actionKey));
    }

    public SupportChatBotReply {
        actionKeys = actionKeys == null ? List.of() : List.copyOf(actionKeys);
    }

    public String actionKey() {
        return actionKeys.isEmpty() ? null : actionKeys.get(0);
    }
}
