package org.example.all_my_trip_project.global.response;

import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        Object data,
        List<FieldErrorDetail> errors
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(
                false,
                code,
                message,
                null,
                List.of()
        );
    }

    public static ErrorResponse validation(List<FieldErrorDetail> errors) {
        return new ErrorResponse(
                false,
                "VALIDATION_ERROR",
                "입력값을 확인해 주세요.",
                null,
                errors
        );
    }

    public record FieldErrorDetail(
            String field,
            String reason
    ) {
    }
}