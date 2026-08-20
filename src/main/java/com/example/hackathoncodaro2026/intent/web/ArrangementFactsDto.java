package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDate;

public record ArrangementFactsDto(
        LocalDate dateOfDeath,
        String rite,
        LocalDate certificateReadyOn,
        Integer mourners
) {
}
