package org.example.all_my_trip_project.domain.support.dto;

/**
 * {@code /user/queue/support-chat/errors}로 보내는 복구 가능한 오류(설계 문서 §3).
 *
 * <p>연결·프로토콜 자체가 깨지는 오류(핸드셰이크 실패 등)는 이 타입이 아니라 STOMP
 * {@code ERROR} 프레임을 그대로 쓴다 — 이 타입은 "구독은 유지된 채 이 요청 하나만
 * 거부됐다"는 뜻이다.
 */
public record SupportChatSocketError(String type, String code, String message, boolean retryable) {
    public static SupportChatSocketError validation(String code, String message) {
        return new SupportChatSocketError("VALIDATION_ERROR", code, message, false);
    }
}
