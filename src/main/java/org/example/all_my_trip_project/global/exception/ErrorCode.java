package org.example.all_my_trip_project.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다."
    ),

    TRIP_DRAFT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행 초안을 찾을 수 없습니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    EMAIL_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    NICKNAME_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),

    ACCOUNT_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "정지된 계정입니다."
    ),

    ACCOUNT_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "탈퇴한 계정입니다."
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
