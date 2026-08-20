package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleIntentParserTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 3);

    private final IntentProperties config = testConfig();
    private final RuleIntentParser parser = new RuleIntentParser(config, DomainProperties.defaults());

    static IntentProperties testConfig() {
        List<IntentProperties.ConstraintRule> constraints = List.of(
                new IntentProperties.ConstraintRule(
                        "indoor", IntentProperties.Kind.HARD, true, "indoor", 0,
                        "indoor", "true", List.of("indoor", "inside", "covered", "under a roof")),
                new IntentProperties.ConstraintRule(
                        "outdoor", IntentProperties.Kind.HARD, true, "outdoor", 0,
                        "indoor", "false", List.of("outdoor", "outside", "open air")),
                new IntentProperties.ConstraintRule(
                        "floodlit", IntentProperties.Kind.HARD, true, "floodlit", 0,
                        "floodlit", "true", List.of("floodlit", "lit", "after dark", "with lights")),
                new IntentProperties.ConstraintRule(
                        "team_sport", IntentProperties.Kind.SOFT, false, "team sport", 10,
                        "team", "true", List.of("team", "with friends", "group"))
        );
        return new IntentProperties(15, 15, 3, 12, 0.25, null, constraints, Map.of());
    }

    record Case(
            String description,
            String text,
            int fallbackPartySize,
            int expectedDuration,
            LocalDate expectedFrom,
            LocalDate expectedTo,
            TimeOfDay expectedTimeOfDay,
            String expectedResourceType,
            List<String> expectedHard,
            List<String> expectedSoft,
            int expectedPartySize
    ) {
        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<Case> cases() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate friday = TODAY.plusDays(4);
        LocalDate saturday = TODAY.plusDays(5);
        LocalDate sunday = TODAY.plusDays(6);
        LocalDate nextMonday = TODAY.plusDays(7);
        LocalDate nextSunday = nextMonday.plusDays(6);
        LocalDate tuesday = TODAY.plusDays(1);
        LocalDate wednesday = TODAY.plusDays(2);

        return Stream.of(
                new Case(
                        "explicit minutes + tomorrow + evening + outdoor + party of two (spec's own example)",
                        "tennis for two tomorrow evening, outdoor court, about 90 minutes",
                        1, 90, tomorrow, tomorrow, TimeOfDay.EVENING, "TENNIS",
                        List.of("outdoor"), List.of(), 2
                ),
                new Case(
                        "default duration + today + indoor + after dark(floodlit) + with a friend",
                        "quick basketball session today, indoor, after dark, with a friend",
                        1, 60, TODAY, TODAY, TimeOfDay.ANY, "BASKETBALL",
                        List.of("indoor", "floodlit"), List.of(), 2
                ),
                new Case(
                        "hours digit + weekend range + team_sport soft + for 4 people",
                        "football match this weekend, team sport, for 4 people, 2 hours",
                        1, 120, saturday, sunday, TimeOfDay.ANY, "FOOTBALL",
                        List.of(), List.of("team_sport"), 4
                ),
                new Case(
                        "swimming synonym + next week + morning + half an hour + solo",
                        "swim solo next week, morning, half an hour",
                        3, 30, nextMonday, nextSunday, TimeOfDay.MORNING, "SWIMMING",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "gym synonym via 'workout' + weekday-or-weekday range + afternoon",
                        "gym workout Tuesday or Wednesday afternoon",
                        1, 60, tuesday, wednesday, TimeOfDay.AFTERNOON, "GYM",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "'an hour' idiom + this week window",
                        "squash for an hour, this week",
                        1, 60, TODAY, sunday, TimeOfDay.ANY, "SQUASH",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "decimal hours ('1.5 hours') + outside phrase + next week + with a friend",
                        "volleyball 1.5 hours outside next week with a friend",
                        1, 90, nextMonday, nextSunday, TimeOfDay.ANY, "VOLLEYBALL",
                        List.of("outdoor"), List.of(), 2
                ),
                new Case(
                        "unrecognised constraint phrase is dropped, never guessed into a key",
                        "tennis tomorrow, needs a referee",
                        1, 60, tomorrow, tomorrow, TimeOfDay.ANY, "TENNIS",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "no sport mentioned -> resourceType is null, not guessed",
                        "book something for 2 hours tomorrow morning for 3 people",
                        1, 120, tomorrow, tomorrow, TimeOfDay.MORNING, null,
                        List.of(), List.of(), 3
                ),
                new Case(
                        "single weekday name resolves to the next such day",
                        "basketball next Friday evening",
                        1, 60, friday, friday, TimeOfDay.EVENING, "BASKETBALL",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "unknown sport word -> resourceType null even with other fields present",
                        "badminton tomorrow",
                        1, 60, tomorrow, tomorrow, TimeOfDay.ANY, null,
                        List.of(), List.of(), 1
                ),
                new Case(
                        "'2h' shorthand + covered/with lights constraints + weekday + fallback party size",
                        "tennis 2h next Tuesday morning, covered court, with lights",
                        5, 120, tuesday, tuesday, TimeOfDay.MORNING, "TENNIS",
                        List.of("indoor", "floodlit"), List.of(), 5
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void extractsExpectedFields(Case c) {
        IntentParser.ParseResult result = parser.parse(c.text(), TODAY, c.fallbackPartySize());
        IntentSpec spec = result.spec();

        assertThat(result.parserUsed()).isEqualTo("rules");
        assertThat(spec.durationMin()).as("durationMin").isEqualTo(c.expectedDuration());
        assertThat(spec.dayFrom()).as("dayFrom").isEqualTo(c.expectedFrom());
        assertThat(spec.dayTo()).as("dayTo").isEqualTo(c.expectedTo());
        assertThat(spec.timeOfDay()).as("timeOfDay").isEqualTo(c.expectedTimeOfDay());
        assertThat(spec.resourceType()).as("resourceType").isEqualTo(c.expectedResourceType());
        assertThat(spec.hardConstraints()).as("hardConstraints")
                .containsExactlyInAnyOrderElementsOf(c.expectedHard());
        assertThat(spec.softConstraints()).as("softConstraints")
                .containsExactlyInAnyOrderElementsOf(c.expectedSoft());
        assertThat(spec.partySize()).as("partySize").isEqualTo(c.expectedPartySize());

        List<String> validKeys = config.constraints().stream().map(IntentProperties.ConstraintRule::key).toList();
        assertThat(validKeys).containsAll(spec.hardConstraints());
        assertThat(validKeys).containsAll(spec.softConstraints());
        List<String> overlap = new java.util.ArrayList<>(spec.hardConstraints());
        overlap.retainAll(spec.softConstraints());
        assertThat(overlap).as("a key must never be both hard and soft").isEmpty();
    }

    @Test
    void neverThrowsOnGibberishInput() {
        IntentParser.ParseResult result = parser.parse("asdkjh qwlekj !!! %%%", TODAY, 1);

        assertThat(result.parserUsed()).isEqualTo("rules");
        IntentSpec spec = result.spec();
        assertThat(spec.durationMin()).isEqualTo(60);
        assertThat(spec.dayFrom()).isEqualTo(TODAY);
        assertThat(spec.dayTo()).isEqualTo(TODAY.plusDays(7));
        assertThat(spec.timeOfDay()).isEqualTo(TimeOfDay.ANY);
        assertThat(spec.resourceType()).isNull();
        assertThat(spec.hardConstraints()).isEmpty();
        assertThat(spec.softConstraints()).isEmpty();
        assertThat(spec.partySize()).isEqualTo(1);
    }

    @Test
    void neverThrowsOnNullOrEmptyInput() {
        assertThat(parser.parse(null, TODAY, 1).spec().durationMin()).isEqualTo(60);
        assertThat(parser.parse("", TODAY, 1).spec().durationMin()).isEqualTo(60);
    }

    @Test
    void usesTodayArgumentNotSystemClock() {
        LocalDate anotherToday = LocalDate.of(2030, 1, 1);
        IntentSpec spec = parser.parse("tennis today", anotherToday, 1).spec();
        assertThat(spec.dayFrom()).isEqualTo(anotherToday);
        assertThat(spec.dayTo()).isEqualTo(anotherToday);
    }
}
