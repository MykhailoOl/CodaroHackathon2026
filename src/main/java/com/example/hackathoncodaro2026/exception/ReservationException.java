package com.example.hackathoncodaro2026.exception;

public class ReservationException extends RuntimeException {

    private final String code;
    private final String field;

    public ReservationException(String message) {
        this(null, null, message);
    }

    public ReservationException(String field, String message) {
        this(null, field, message);
    }

    public ReservationException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
