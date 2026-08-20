package com.example.hackathoncodaro2026.intent.web;

import java.util.List;

public record SuggestResponse(
        IntentSpecDto spec,
        String parserUsed,
        List<SuggestionDto> suggestions,
        List<RelaxStepDto> relaxationTrail
) {
}
