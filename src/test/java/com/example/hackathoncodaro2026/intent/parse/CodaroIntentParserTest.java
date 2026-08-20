package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodaroIntentParserTest {

    private final IntentProperties config = RuleIntentParserTest.testConfig();

    @ParameterizedTest
    @CsvSource({
            "'', '', ''",
            "' ', ' ', ' '",
            "http://localhost:1234/v1, '', model",
            "http://localhost:1234/v1, key, ''",
            "'', key, model",
    })
    void isNotConfiguredWhenAnyCredentialIsMissing(String baseUrl, String apiKey, String model) {
        CodaroIntentParser parser = new CodaroIntentParser(baseUrl, apiKey, model, config, DomainProperties.defaults());
        assertThat(parser.isConfigured()).isFalse();
    }

    @Test
    void isConfiguredWhenAllThreeCredentialsArePresent() {
        CodaroIntentParser parser = new CodaroIntentParser("http://localhost:1234/v1", "sk-test", "some-model", config, DomainProperties.defaults());
        assertThat(parser.isConfigured()).isTrue();
    }

    @Test
    void parseThrowsWithoutAttemptingNetworkWhenNotConfigured() {
        CodaroIntentParser parser = new CodaroIntentParser("", "", "", config, DomainProperties.defaults());

        assertThatThrownBy(() -> parser.parse("tennis tomorrow", LocalDate.of(2024, 6, 3), 1))
                .isInstanceOf(IntentParseException.class);
    }
}
