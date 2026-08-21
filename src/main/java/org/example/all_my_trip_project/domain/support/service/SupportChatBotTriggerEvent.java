package org.example.all_my_trip_project.domain.support.service;

/**
 * 방이 {@code BOT} 상태로 새로 열렸거나, 그 상태에서 손님이 메시지를 보냈을 때 발행한다.
 *
 * <p>{@link SupportChatBotOrchestrator}가 트랜잭션 커밋 뒤 이 이벤트를 받아 Gemini를 부른다.
 * 방을 새로 열었을 때는 대화 내역이 비어 있고, {@link SupportChatBotClient}가 그 경우를
 * "첫 인사"로 다룬다 — 이벤트 타입을 따로 두지 않는다.
 */
public record SupportChatBotTriggerEvent(Long roomId) {
}
