package com.example.hackathoncodaro2026.dto.assistant;

import java.math.BigDecimal;

public record AssistantQuoteDto(BigDecimal amount, String currency, String expectedStatus) {
}
