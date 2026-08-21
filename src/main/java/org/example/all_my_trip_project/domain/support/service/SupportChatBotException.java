package org.example.all_my_trip_project.domain.support.service;

/** 상담 봇 호출이 실패했거나(네트워크, API 키, 시간 초과 등) 응답 형식이 올바르지 않을 때. */
public class SupportChatBotException extends RuntimeException {
    public SupportChatBotException(String message) {
        super(message);
    }

    public SupportChatBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
