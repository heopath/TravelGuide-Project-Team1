package org.example.all_my_trip_project.domain.support.service;

/**
 * 상담 봇 호출이 실패했거나(네트워크, API 키, 시간 초과 등) 응답 형식이 올바르지 않을 때.
 *
 * <p>{@link #isRetryable()}로 <b>일시적 실패</b>와 <b>영구적 실패</b>를 구분한다. 이 구분이
 * 중요한 이유는 방의 상태를 가르기 때문이다 — 일시적 실패는 방을 {@code BOT}에 두고 다시
 * 시도할 수 있게 하지만, 영구적 실패(예: API 키 미설정)는 재시도 안내 대신 상담원 연결
 * 의사를 확인해야 한다. 어느 경우든 사용자의 동의 없이 자동으로 대기 상태로 넘기지 않는다.
 *
 * <p>일시적 오류마다 {@code WAITING}으로 넘기면 손님이 매번 직접 봇 복귀나 새 상담을
 * 선택해야 하므로, 복구 가능한 오류는 현재 {@code BOT} 방에서 바로 재시도할 수 있게 둔다.
 */
public class SupportChatBotException extends RuntimeException {

    /** 다시 불러 볼 가치가 있는 실패인가. */
    private final boolean retryable;

    public SupportChatBotException(String message) {
        this(message, true);
    }

    public SupportChatBotException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public SupportChatBotException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public SupportChatBotException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
