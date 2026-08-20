package com.example.hackathoncodaro2026.dto.assistant;

public record AssistantSessionDto(
        boolean authenticated,
        Long userId,
        String username,
        String role,
        boolean phoneRequired,
        String expectedStatus
) {
}
