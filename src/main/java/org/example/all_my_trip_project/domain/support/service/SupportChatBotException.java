package org.example.all_my_trip_project.domain.support.service;

/**
 * 상담 봇 호출이 실패했거나(네트워크, API 키, 시간 초과 등) 응답 형식이 올바르지 않을 때.
 *
 * <p>{@link #isRetryable()}로 <b>일시적 실패</b>와 <b>영구적 실패</b>를 구분한다. 이 구분이
 * 중요한 이유는 방의 상태를 가르기 때문이다 — 일시적 실패는 방을 {@code BOT}에 두고 다시
 * 시도할 수 있게 하지만, 영구적 실패(예: API 키 미설정)는 몇 번을 다시 불러도 같은 결과라
 * 곧장 상담원에게 넘겨야 한다.
 *
 * <p>한번 {@code WAITING}이 되면 그 방은 다시 {@code BOT}으로 돌아오지 못하므로(상태 전환에
 * {@code → BOT} 경로가 없다), 일시적 오류로 넘겨 버리면 그 손님은 봇을 영영 못 쓴다.
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
