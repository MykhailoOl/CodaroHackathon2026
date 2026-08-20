package com.example.hackathoncodaro2026.dto.assistant;

import java.math.BigDecimal;

public record AssistantPackageDto(String code, String label, String description, int durationMinutes, BigDecimal amount) {
}
