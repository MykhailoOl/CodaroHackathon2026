package com.example.hackathoncodaro2026.dto.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AssistantPreviewDto(
        List<LocalDate> dates,
        BigDecimal amount,
        String currency,
        String expectedStatus,
        String notice
) {
}
