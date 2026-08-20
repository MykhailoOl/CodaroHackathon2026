package com.example.hackathoncodaro2026.intent.web;

import com.example.hackathoncodaro2026.intent.model.TimeOfDay;

import java.time.LocalDate;
import java.util.List;

public record IntentSpecDto(
        int durationMin,
        LocalDate dayFrom,
        LocalDate dayTo,
        TimeOfDay timeOfDay,
        List<String> hardConstraints,
        List<String> softConstraints,
        String resourceType,
        int partySize
) {
}
