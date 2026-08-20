package com.example.hackathoncodaro2026.exception;

import org.springframework.http.HttpStatus;

public class AssistantException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final String field;
    private final String step;

    public AssistantException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST, null, null);
    }

    public AssistantException(String code, String message, HttpStatus status) {
        this(code, message, status, null, null);
    }

    public AssistantException(String code, String message, HttpStatus status, String field, String step) {
        super(message);
        this.code = code;
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
        this.field = field;
        this.step = step;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }

    public String getStep() {
        return step;
    }
}
