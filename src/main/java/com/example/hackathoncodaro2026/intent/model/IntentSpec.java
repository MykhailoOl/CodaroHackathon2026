package com.example.hackathoncodaro2026.intent.model;

import java.time.LocalDate;
import java.util.List;

public record IntentSpec(
        int durationMin,
        LocalDate dayFrom,
        LocalDate dayTo,
        TimeOfDay timeOfDay,
        List<String> hardConstraints,
        List<String> softConstraints,
        String resourceType,
        int partySize
) {
    public IntentSpec {
        hardConstraints = hardConstraints == null ? List.of() : List.copyOf(hardConstraints);
        softConstraints = softConstraints == null ? List.of() : List.copyOf(softConstraints);
        timeOfDay = timeOfDay == null ? TimeOfDay.ANY : timeOfDay;
        partySize = Math.max(1, partySize);
    }
}
