package com.example.hackathoncodaro2026.intent.derive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The span of dates a service may legally and religiously fall within, derived from
 * {@link ArrangementFacts} rather than chosen by anyone.
 *
 * <p>{@code derivation} is the point of the record as much as the dates are: a family
 * is being told they cannot pick a date, so they are owed the reasoning in plain
 * language. Each entry is one sentence naming one constraint.
 *
 * @param earliest    first feasible date (certificate release / today)
 * @param latest      last feasible date (rite custom, capped by statute)
 * @param rite        the observance the latest date came from, or null
 * @param derivation  human-readable reasons, in the order they were applied
 * @param decisionBy  when an answer is needed for the venue hold to survive
 * @param feasible    false when the certificate cannot arrive before the rite's deadline
 */
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
