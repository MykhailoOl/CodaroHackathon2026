package com.example.hackathoncodaro2026.intent.web;

import java.util.List;

/**
 * {@code window} and {@code facts} were added for the funeral pivot and are appended,
 * never substituted: every field the original contract defined keeps its name, type and
 * meaning. Both are null for a request that states no date of death.
 */
public record SuggestResponse(
        IntentSpecDto spec,
        String parserUsed,
        List<SuggestionDto> suggestions,
        List<RelaxStepDto> relaxationTrail,
        ServiceWindowDto window,
        ArrangementFactsDto facts
) {
}
