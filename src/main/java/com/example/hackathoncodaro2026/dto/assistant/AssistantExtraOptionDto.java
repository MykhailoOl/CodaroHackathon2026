package com.example.hackathoncodaro2026.dto.assistant;

import java.math.BigDecimal;

public record AssistantExtraOptionDto(Long id, String name, BigDecimal amount, String pricingMode) {
}
