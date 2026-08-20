package com.example.hackathoncodaro2026.dto.assistant;

public record AssistantErrorResponse(String code, String message, String field, String step) {

    public AssistantErrorResponse(String code, String message) {
        this(code, message, null, null);
    }
}
