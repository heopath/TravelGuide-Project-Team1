package org.example.all_my_trip_project.domain.ai.service;

public class AiModelException extends RuntimeException {

    public AiModelException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiModelException(String message) {
        super(message);
    }
}
