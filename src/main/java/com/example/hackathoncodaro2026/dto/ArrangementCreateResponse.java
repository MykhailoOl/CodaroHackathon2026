package com.example.hackathoncodaro2026.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ArrangementCreateResponse(
        Long id,
        String status,
        BigDecimal amount,
        String formattedAmount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<LocalDate> dates
) {
}
