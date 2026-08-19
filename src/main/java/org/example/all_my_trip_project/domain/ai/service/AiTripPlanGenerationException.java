package org.example.all_my_trip_project.domain.ai.service;

/**
 * AI 여행 초안 생성이 실패한 사유를 화면까지 전달하기 위한 예외.
 *
 * <p>기존에는 {@code IllegalStateException}을 던졌는데 전용 핸들러가 없어
 * {@code ApiExceptionHandler}의 만능 {@code Exception} 핸들러가 받았고,
 * 무엇이 잘못됐든 "서버에서 오류가 발생했습니다" 500으로 뭉개졌다.
 * 키 문제인지 한도 초과인지 응답 형식 문제인지 구분할 수 없어,
 * 운영 장애(#234)를 EC2 로그를 직접 봐야만 확인할 수 있었다.
 */
public class AiTripPlanGenerationException extends RuntimeException {

    public AiTripPlanGenerationException(String message) {
        super(message);
    }

    public AiTripPlanGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
