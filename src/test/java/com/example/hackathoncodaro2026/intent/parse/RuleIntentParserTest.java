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
                        "indoor", IntentProperties.Kind.HARD, true, "indoors", 0,
                        "indoor", "true", List.of("indoor", "indoors", "inside", "under cover")),
                new IntentProperties.ConstraintRule(
                        "graveside", IntentProperties.Kind.HARD, true, "graveside", 0,
                        "graveside", "true", List.of("graveside", "at the grave", "cemetery", "interment")),
                new IntentProperties.ConstraintRule(
                        "religious", IntentProperties.Kind.HARD, true, "religious rite", 0,
                        "religious", "true", List.of("priest", "religious", "church", "mass", "requiem")),
                new IntentProperties.ConstraintRule(
                        "catering", IntentProperties.Kind.SOFT, false, "refreshments", 10,
                        "catering", "true", List.of("refreshments", "catering", "food"))
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
                        "explicit minutes + tomorrow + evening + priest + party of two",
                        "chapel for two tomorrow evening, priest, about 90 minutes",
                        1, 90, tomorrow, tomorrow, TimeOfDay.EVENING, "CHAPEL",
                        List.of("religious"), List.of(), 2
                ),
                new Case(
                        "default duration + today + indoors + requiem + with a friend",
                        "quick cremation today, indoors, requiem, with a friend",
                        1, 60, TODAY, TODAY, TimeOfDay.ANY, "CREMATION",
                        List.of("indoor", "religious"), List.of(), 2
                ),
                new Case(
                        "hours digit + weekend range + catering soft + for 4 people",
                        "wake this weekend, refreshments, for 4 people, 2 hours",
                        1, 120, saturday, sunday, TimeOfDay.ANY, "RECEPTION",
                        List.of(), List.of("catering"), 4
                ),
                new Case(
                        "viewing synonym + next week + morning + half an hour + solo",
                        "repose solo next week, morning, half an hour",
                        3, 30, nextMonday, nextSunday, TimeOfDay.MORNING, "VIEWING",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "transport synonym via 'hearse' + weekday-or-weekday range + afternoon",
                        "hearse Tuesday or Wednesday afternoon",
                        1, 60, tuesday, wednesday, TimeOfDay.AFTERNOON, "TRANSPORT",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "'an hour' idiom + this week window",
                        "repatriation for an hour, this week",
                        1, 60, TODAY, sunday, TimeOfDay.ANY, "REPATRIATION",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "decimal hours ('1.5 hours') + graveside + next week + with a friend",
                        "burial 1.5 hours graveside next week with a friend",
                        1, 90, nextMonday, nextSunday, TimeOfDay.ANY, "BURIAL",
                        List.of("graveside"), List.of(), 2
                ),
                new Case(
                        "unrecognised constraint phrase is dropped, never guessed into a key",
                        "chapel tomorrow, needs a harpist",
                        1, 60, tomorrow, tomorrow, TimeOfDay.ANY, "CHAPEL",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "no service mentioned -> resourceType is null, not guessed",
                        "book something for 2 hours tomorrow morning for 3 people",
                        1, 120, tomorrow, tomorrow, TimeOfDay.MORNING, null,
                        List.of(), List.of(), 3
                ),
                new Case(
                        "single weekday name resolves to the next such day",
                        "cremation next Friday evening",
                        1, 60, friday, friday, TimeOfDay.EVENING, "CREMATION",
                        List.of(), List.of(), 1
                ),
                new Case(
                        "unknown service word -> resourceType null even with other fields present",
                        "embalming tomorrow",
                        1, 60, tomorrow, tomorrow, TimeOfDay.ANY, null,
                        List.of(), List.of(), 1
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
