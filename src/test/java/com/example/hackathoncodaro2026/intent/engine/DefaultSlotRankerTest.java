package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.Kind;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RankResult;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import com.example.hackathoncodaro2026.intent.model.Suggestion;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSlotRankerTest {

    private static final LocalTime OPENING = LocalTime.of(8, 0);
    private static final LocalTime CLOSING = LocalTime.of(20, 0);

    private final DefaultSlotRanker ranker = new DefaultSlotRanker();

    private ResourceSlice resource(long id, String name, Map<String, Object> attributes) {
        return new ResourceSlice(id, name, "Site 1", "TYPE1", 4, OPENING, CLOSING, 60, attributes);
    }

    private IntentProperties config(List<ConstraintRule> constraints) {
        return new IntentProperties(15, 10, 3, 12, 0.25,
                new IntentProperties.Weights(20, 15, 15, 20, 15, 15), constraints, Map.of());
    }

    @Test
    void rankingIsDeterministicAndStableAcrossRepeatedRuns() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDate day = now.toLocalDate();
        List<ResourceSlice> resources = List.of(
                resource(1L, "Room A", Map.of("quiet", "yes", "view", "yes")),
                resource(2L, "Room B", Map.of("quiet", "yes", "view", "no")),
                resource(3L, "Room C", Map.of("quiet", "no", "view", "yes")));
        ScheduleSnapshot snapshot = new ScheduleSnapshot(resources, List.of(), now, 1L, null);
        IntentProperties config = config(List.of(
                new ConstraintRule("req_quiet", Kind.HARD, true, "quiet requirement", 0, "quiet", "yes", List.of()),
                new ConstraintRule("pref_view", Kind.SOFT, false, "view preference", 8, "view", "yes", List.of())));
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY, List.of("req_quiet"), List.of("pref_view"),
                "TYPE1", 2);

        RankResult first = ranker.rank(spec, snapshot, config);
        RankResult second = ranker.rank(spec, snapshot, config);
        RankResult third = ranker.rank(spec, snapshot, config);

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void neverSuggestsAResourceFailingAnActiveHardConstraint() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDate day = now.toLocalDate();
        List<ResourceSlice> resources = List.of(
                resource(1L, "Room A", Map.of("quiet", "yes")),
                resource(2L, "Room B", Map.of("quiet", "yes")),
                resource(3L, "Room C", Map.of("quiet", "no")));
        ScheduleSnapshot snapshot = new ScheduleSnapshot(resources, List.of(), now, 1L, null);
        IntentProperties config = config(List.of(
                new ConstraintRule("req_quiet", Kind.HARD, true, "quiet requirement", 0, "quiet", "yes", List.of())));
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY, List.of("req_quiet"), List.of(), "TYPE1", 1);

        RankResult result = ranker.rank(spec, snapshot, config);

        assertTrue(result.relaxationTrail().isEmpty(), "plenty of supply, no relaxation should have been needed");
        assertFalse(result.suggestions().isEmpty());
        for (Suggestion s : result.suggestions()) {
            assertFalse(s.resourceId() == 3L, "Room C fails the active hard constraint and must never be suggested");
        }
    }

    @Test
    void neverSuggestsAnOverlappingOrOutOfHoursSlot() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDate day = now.toLocalDate();
        ResourceSlice r = resource(1L, "Room A", Map.of());
        ReservationSlice existing = new ReservationSlice(1L, 1L, 9L,
                day.atTime(10, 0), day.atTime(11, 0), 4);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(r), List.of(existing), now, 1L, null);
        IntentProperties config = config(List.of());
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY, List.of(), List.of(), "TYPE1", 1);

        RankResult result = ranker.rank(spec, snapshot, config);

        assertFalse(result.suggestions().isEmpty());
        for (Suggestion s : result.suggestions()) {
            assertFalse(s.start().toLocalTime().isBefore(OPENING));
            assertFalse(s.end().toLocalTime().isAfter(CLOSING));
            boolean overlapsExisting = existing.start().isBefore(s.end()) && s.start().isBefore(existing.end());
            assertFalse(overlapsExisting, "suggestion " + s.start() + " overlaps an existing reservation");
        }
    }

    @Test
    void relaxationWidensTheDayWindowAndTagsSuggestionsAsRelaxed() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDate day = LocalDate.of(2026, 8, 25);
        ResourceSlice r = resource(1L, "Room A", Map.of());
        ReservationSlice almostAllDay = new ReservationSlice(1L, 1L, 9L,
                day.atTime(8, 0), day.atTime(19, 0), 4);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(r), List.of(almostAllDay), now, 1L, null);
        IntentProperties config = config(List.of());
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY, List.of(), List.of(), "TYPE1", 1);

        RankResult result = ranker.rank(spec, snapshot, config);

        assertFalse(result.relaxationTrail().isEmpty(), "must have relaxed to find a second option");
        assertTrue(result.suggestions().size() >= 2);
        for (Suggestion s : result.suggestions()) {
            assertTrue(s.relaxed().contains("day_window"),
                    "suggestion produced after relaxation must carry the dropped key");
        }
    }

    @Test
    void neverReturnsEmptySuggestionsWithAnEmptyRelaxationTrail() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDate day = now.toLocalDate();
        ResourceSlice r = resource(1L, "Room A", Map.of("locked", "no"));
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(r), List.of(), now, 1L, null);
        IntentProperties config = config(List.of(
                new ConstraintRule("req_locked", Kind.HARD, false, "locked requirement", 0, "locked", "yes",
                        List.of())));
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY, List.of("req_locked"), List.of(), "TYPE1", 1);

        RankResult result = ranker.rank(spec, snapshot, config);

        assertTrue(result.suggestions().isEmpty());
        assertFalse(result.relaxationTrail().isEmpty(), "empty suggestions with an empty trail is a bug");
    }
}
