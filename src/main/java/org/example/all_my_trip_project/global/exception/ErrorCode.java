package org.example.all_my_trip_project.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    EMAIL_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    NICKNAME_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    PASSWORD_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 일치하지 않습니다."
    ),

    NEW_PASSWORD_SAME_AS_CURRENT(
            HttpStatus.BAD_REQUEST,
            "새 비밀번호는 현재 비밀번호와 다르게 설정해주세요."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다."
    ),

    AI_REQUEST_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "AI 추천 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    ),

    ACCOUNT_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "정지된 계정입니다."
    ),

    ACCOUNT_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "탈퇴한 계정입니다."
    ),

    TRAVEL_STYLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용할 수 없는 여행 스타일이 포함되어 있습니다."
    ),

    TRAVEL_STYLE_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "동일한 여행 스타일을 중복해서 저장할 수 없습니다."
    ),

    WEATHER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "날씨 서비스가 아직 설정되지 않았습니다."
    ),

    WEATHER_DATE_OUT_OF_RANGE(
            HttpStatus.BAD_REQUEST,
            "날씨는 오늘부터 3일 이내 일정만 확인할 수 있습니다."
    ),

    INVALID_TRIP_REQUEST(
            HttpStatus.BAD_REQUEST,
            "여행 생성 요청값이 올바르지 않습니다."
    ),

    INVALID_TRIP_PERIOD(
            HttpStatus.BAD_REQUEST,
            "여행 기간은 시작일과 종료일을 포함해 1일 이상 30일 이하여야 합니다."
    ),

    INVALID_COMPANION_COUNT(
            HttpStatus.BAD_REQUEST,
            "동행 인원은 1명 이상 20명 이하여야 하며 혼자 여행은 1명이어야 합니다."
    ),

    TRIP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행을 찾을 수 없습니다."
    ),

    TRIP_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "여행을 생성하지 못했습니다."
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}