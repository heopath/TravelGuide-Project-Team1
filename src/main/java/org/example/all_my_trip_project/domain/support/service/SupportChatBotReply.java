package org.example.all_my_trip_project.domain.support.service;

import java.util.List;

/**
 * 봇의 답변 한 건.
 *
 * @param content  손님에게 보여줄 내용. 내부 판단 표시는 이미 걷어낸 상태다.
 * @param handoffDecision AI가 대화 전체를 읽고 내린 상담원 연결 판단.
 * @param actionKeys 답변과 함께 보여줄 내부 화면 이동 액션. 표시 순서대로 최대 3개다.
 */
public record SupportChatBotReply(
        String content,
        SupportChatHandoffDecision handoffDecision,
        List<String> actionKeys,
        List<SupportChatPlaceSelection> placeSelections
) {
    public SupportChatBotReply(String content, boolean handoff) {
        this(content, handoff ? SupportChatHandoffDecision.CONNECT : SupportChatHandoffDecision.NONE, List.of(), List.of());
    }

    public SupportChatBotReply(String content, boolean handoff, String actionKey) {
        this(content, handoff ? SupportChatHandoffDecision.CONNECT : SupportChatHandoffDecision.NONE,
                actionKey == null ? List.of() : List.of(actionKey), List.of());
    }

    public SupportChatBotReply(String content, boolean handoff, List<String> actionKeys) {
        this(content, handoff ? SupportChatHandoffDecision.CONNECT : SupportChatHandoffDecision.NONE, actionKeys, List.of());
    }

    public SupportChatBotReply(String content, SupportChatHandoffDecision handoffDecision, List<String> actionKeys) {
        this(content, handoffDecision, actionKeys, List.of());
    }

    public SupportChatBotReply {
        handoffDecision = handoffDecision == null ? SupportChatHandoffDecision.NONE : handoffDecision;
        actionKeys = actionKeys == null ? List.of() : List.copyOf(actionKeys);
        placeSelections = placeSelections == null ? List.of() : List.copyOf(placeSelections);
    }

    public boolean handoff() {
        return handoffDecision == SupportChatHandoffDecision.CONNECT;
    }

    public String actionKey() {
        return actionKeys.isEmpty() ? null : actionKeys.get(0);
    }
}
