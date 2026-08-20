package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDate;

/** What the request text stated about the deceased. Fields are null when unstated. */
public record ArrangementFactsDto(
        LocalDate dateOfDeath,
        String rite,
        LocalDate certificateReadyOn,
        Integer mourners
) {
}
