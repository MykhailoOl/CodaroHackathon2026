package com.example.hackathoncodaro2026.intent.parse;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Primary
public class CompositeIntentParser implements IntentParser {

    private final CodaroIntentParser llmParser;
    private final RuleIntentParser ruleParser;

    public CompositeIntentParser(CodaroIntentParser llmParser, RuleIntentParser ruleParser) {
        this.llmParser = llmParser;
        this.ruleParser = ruleParser;
    }

    @Override
    public ParseResult parse(String text, LocalDate today, int partySize) {
        if (llmParser.isConfigured()) {
            try {
                return llmParser.parse(text, today, partySize);
            } catch (Exception e) {
            }
        }
        return ruleParser.parse(text, today, partySize);
    }
}
