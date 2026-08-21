package org.example.all_my_trip_project.domain.support.service;

/**
 * 봇의 답변 한 건.
 *
 * @param content  손님에게 보여줄 내용. {@code [HANDOFF]} 표시는 이미 걷어낸 상태다.
 * @param handoff  true면 방을 {@code WAITING}으로 넘긴다(상담원 요청 / 정책 범위 밖 / 반복 미해결).
 */
public record SupportChatBotReply(String content, boolean handoff) {
}
