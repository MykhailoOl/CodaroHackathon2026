package com.example.hackathoncodaro2026.intent.derive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ServiceWindow(
        LocalDate earliest,
        LocalDate latest,
        String rite,
        List<String> derivation,
        LocalDateTime decisionBy,
        boolean feasible
) {

    public ServiceWindow {
        derivation = derivation == null ? List.of() : List.copyOf(derivation);
    }
}
