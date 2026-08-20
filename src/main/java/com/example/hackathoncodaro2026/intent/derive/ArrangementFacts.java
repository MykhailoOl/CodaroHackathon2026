package com.example.hackathoncodaro2026.intent.derive;

import java.time.LocalDate;

public record ArrangementFacts(
        LocalDate dateOfDeath,
        String rite,
        LocalDate certificateReadyOn,
        Integer mourners
) {

    public static ArrangementFacts none() {
        return new ArrangementFacts(null, null, null, null);
    }

    public boolean schedulable() {
        return dateOfDeath != null;
    }
}
