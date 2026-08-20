package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The derived feasible window for a service, sent alongside the suggestions.
 *
 * <p>Additive: clients that predate the pivot ignore this field and keep working.
 *
 * @param derivation plain-language reasons the window is what it is; a family being
 *                   told they cannot pick a date is owed the reasoning
 * @param feasible   false when the certificate cannot arrive before the deadline,
 *                   which needs a human at the funeral home, not a retry
 * @param note       how any date the family asked for was reconciled with the window
 */
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
