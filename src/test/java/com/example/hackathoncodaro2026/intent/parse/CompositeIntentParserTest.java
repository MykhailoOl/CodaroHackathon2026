package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CompositeIntentParserTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 3);

    private final IntentProperties config = RuleIntentParserTest.testConfig();

    @Test
    void fallsBackToRulesWhenLlmIsUnconfigured() {
        CodaroIntentParser unconfigured = new CodaroIntentParser("", "", "", config, DomainProperties.defaults());
        RuleIntentParser ruleParser = new RuleIntentParser(config, DomainProperties.defaults());
        CompositeIntentParser composite = new CompositeIntentParser(unconfigured, ruleParser);

        IntentParser.ParseResult result = composite.parse(
                "chapel for two tomorrow evening, priest, about 90 minutes", TODAY, 1);

        assertThat(result.parserUsed()).isEqualTo("rules");
        IntentSpec spec = result.spec();
        assertThat(spec.durationMin()).isEqualTo(90);
        assertThat(spec.resourceType()).isEqualTo("CHAPEL");
    }

    @Test
    void doesNotThrowOrLogScarilyWhenCredentialsAreAbsent() {
        CodaroIntentParser unconfigured = new CodaroIntentParser(null, null, null, config, DomainProperties.defaults());
        CompositeIntentParser composite = new CompositeIntentParser(unconfigured, new RuleIntentParser(config, DomainProperties.defaults()));

        assertThatCode(() -> composite.parse("hearse tomorrow", TODAY, 1)).doesNotThrowAnyException();
    }

    @Test
    void fallsBackToRulesWhenConfiguredLlmParserThrows() {
        CodaroIntentParser configuredButBroken = new CodaroIntentParser(
                "http://localhost:1234/v1", "sk-test", "some-model", config, DomainProperties.defaults()) {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public ParseResult parse(String text, LocalDate today, int partySize) {
                throw new IntentParseException("simulated network failure");
            }
        };
        CompositeIntentParser composite = new CompositeIntentParser(configuredButBroken, new RuleIntentParser(config, DomainProperties.defaults()));

        IntentParser.ParseResult result = composite.parse("repatriation tomorrow", TODAY, 1);

        assertThat(result.parserUsed()).isEqualTo("rules");
        assertThat(result.spec().resourceType()).isEqualTo("REPATRIATION");
    }

    @Test
    void fallsBackToRulesWhenConfiguredLlmParserReturnsInvalidResultViaException() {
        CodaroIntentParser configuredButInvalid = new CodaroIntentParser(
                "http://localhost:1234/v1", "sk-test", "some-model", config, DomainProperties.defaults()) {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public ParseResult parse(String text, LocalDate today, int partySize) {
                throw new IntentParseException("LLM returned dayFrom after dayTo");
            }
        };
        CompositeIntentParser composite = new CompositeIntentParser(configuredButInvalid, new RuleIntentParser(config, DomainProperties.defaults()));

        IntentParser.ParseResult result = composite.parse("cremation next Friday evening", TODAY, 1);

        assertThat(result.parserUsed()).isEqualTo("rules");
        assertThat(result.spec().resourceType()).isEqualTo("CREMATION");
    }
}
