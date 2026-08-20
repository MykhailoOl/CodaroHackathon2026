package com.example.hackathoncodaro2026.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ArrangementPreview(
        List<LocalDate> dates,
        BigDecimal amount,
        String currency,
        String expectedStatus,
        String notice
) {
}
