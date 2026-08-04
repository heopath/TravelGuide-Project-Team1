package org.example.all_my_trip_project.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.global.response.ErrorResponse;
import org.example.all_my_trip_project.global.response.ErrorResponse.FieldErrorDetail;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(AiModelException.class)
    public ResponseEntity<ErrorResponse> handleAiModelException(
            AiModelException exception
    ) {
        log.warn("AI model request failed", exception);

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(
                        "AI_GENERATION_FAILED",
                        "AI 추천을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        "BAD_REQUEST",
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorDetail> errors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new FieldErrorDetail(
                                error.getField(),
                                error.getDefaultMessage()
                        ))
                        .toList();

        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception
    ) {
        log.error("처리되지 않은 서버 오류", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        "INTERNAL_SERVER_ERROR",
                        "서버에서 오류가 발생했습니다."
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }
}
