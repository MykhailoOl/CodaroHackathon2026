package com.example.hackathoncodaro2026.intent.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public record SuggestionDto(
        long resourceId,
        String resourceName,
        String facilityName,
        LocalDateTime start,
        LocalDateTime end,
        double score,
        String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String price,
        List<ScoreTermDto> terms,
        List<String> relaxed
) {
}
