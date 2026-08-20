package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ServiceWindowDto(
        LocalDate earliest,
        LocalDate latest,
        String rite,
        List<String> derivation,
        LocalDateTime decisionBy,
        boolean feasible,
        String note
) {
}
