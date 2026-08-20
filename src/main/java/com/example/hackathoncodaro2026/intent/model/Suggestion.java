package com.example.hackathoncodaro2026.intent.model;

import java.time.LocalDateTime;
import java.util.List;

public record Suggestion(
        long resourceId,
        String resourceName,
        String facilityName,
        LocalDateTime start,
        LocalDateTime end,
        double score,
        List<ScoreTerm> terms,
        String reason,
        List<String> relaxed
) {
    public Suggestion {
        terms = terms == null ? List.of() : List.copyOf(terms);
        relaxed = relaxed == null ? List.of() : List.copyOf(relaxed);
    }
}
