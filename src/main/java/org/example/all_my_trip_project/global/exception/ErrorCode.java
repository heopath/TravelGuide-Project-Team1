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