package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A candidate must never be generated outside opening/closing hours, and
 * never in the past relative to {@code snapshot.now()} — even when "now"
 * falls in the middle of today's open window.
 */
class CandidateGeneratorTest {

    private static final LocalTime OPENING = LocalTime.of(8, 0);
    private static final LocalTime CLOSING = LocalTime.of(20, 0);

    private ResourceSlice resource() {
        return new ResourceSlice(1L, "Slot A", "Site 1", "TYPE1", 4, OPENING, CLOSING, 60, Map.of());
    }

    private IntentProperties config() {
        return new IntentProperties(15, 10, 3, 12, 0.25,
                new IntentProperties.Weights(20, 15, 15, 20, 15, 15), List.of(), Map.of());
    }

    @Test
    void neverGeneratesACandidateBeforeNowWhenNowIsMidwayThroughToday() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 13, 0);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(), List.of(), now, 1L, null);
        IntentSpec spec = new IntentSpec(60, now.toLocalDate(), now.toLocalDate().plusDays(1), TimeOfDay.ANY,
                List.of(), List.of(), "TYPE1", 1);

        List<Candidate> candidates = CandidateGenerator.generate(resource(), spec, snapshot, config(),
                spec.dayFrom(), spec.dayTo());

        assertFalse(candidates.isEmpty());
        for (Candidate c : candidates) {
            assertFalse(c.start().isBefore(now), "candidate " + c.start() + " is before now " + now);
        }
    }

    @Test
    void neverGeneratesACandidateOutsideOpeningOrClosingHours() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 6, 0);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(), List.of(), now, 1L, null);
        IntentSpec spec = new IntentSpec(60, now.toLocalDate(), now.toLocalDate().plusDays(2), TimeOfDay.ANY,
                List.of(), List.of(), "TYPE1", 1);

        List<Candidate> candidates = CandidateGenerator.generate(resource(), spec, snapshot, config(),
                spec.dayFrom(), spec.dayTo());

        assertFalse(candidates.isEmpty());
        for (Candidate c : candidates) {
            assertTrue(!c.start().toLocalTime().isBefore(OPENING), "start before opening: " + c.start());
            assertTrue(!c.end().toLocalTime().isAfter(CLOSING), "end after closing: " + c.end());
        }
    }

    @Test
    void skipsWholeDaysThatHaveAlreadyFullyElapsed() {
        // "now" is after closing on day 1: day 1 must contribute no candidates at all.
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 21, 0);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(), List.of(), now, 1L, null);
        IntentSpec spec = new IntentSpec(60, now.toLocalDate(), now.toLocalDate().plusDays(1), TimeOfDay.ANY,
                List.of(), List.of(), "TYPE1", 1);

        List<Candidate> candidates = CandidateGenerator.generate(resource(), spec, snapshot, config(),
                spec.dayFrom(), spec.dayTo());

        assertFalse(candidates.isEmpty());
        for (Candidate c : candidates) {
            assertTrue(c.start().toLocalDate().isEqual(now.toLocalDate().plusDays(1)));
        }
    }

    @Test
    void freeIntervalShorterThanDurationYieldsNoCandidates() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        LocalDateTime resStart = now.toLocalDate().atTime(8, 30);
        ReservationSlice blocksAlmostAll = new ReservationSlice(1L, 1L, 9L, resStart, resStart.plusHours(11), 4);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(), List.of(blocksAlmostAll), now, 1L, null);
        // Free window left is 8:00-8:30 (30 min) but duration is 60 min -> must yield nothing that day.
        IntentSpec spec = new IntentSpec(60, now.toLocalDate(), now.toLocalDate(), TimeOfDay.ANY,
                List.of(), List.of(), "TYPE1", 1);

        List<Candidate> candidates = CandidateGenerator.generate(resource(), spec, snapshot, config(),
                spec.dayFrom(), spec.dayTo());

        assertTrue(candidates.isEmpty());
    }

    /**
     * config().bufferMin() is 10 and config().granularityMin() is 15 — neither divides
     * the resource's 60-minute slot grid, so a naive buffer/granularity walk (the old
     * behavior) produces starts like 08:10 or 08:25 that ReservationServiceImpl.isAligned
     * rejects at confirm time with "Start time must match a 60-minute slot". Every
     * generated candidate must land on opening + a whole multiple of slotDurationMinutes.
     */
    @Test
    void everyCandidateStartAlignsToTheResourcesSlotGrid() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 7, 0);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(List.of(), List.of(), now, 1L, null);
        IntentSpec spec = new IntentSpec(60, now.toLocalDate(), now.toLocalDate(), TimeOfDay.ANY,
                List.of(), List.of(), "TYPE1", 1);

        List<Candidate> candidates = CandidateGenerator.generate(resource(), spec, snapshot, config(),
                spec.dayFrom(), spec.dayTo());

        assertFalse(candidates.isEmpty());
        for (Candidate c : candidates) {
            long minutesSinceOpen = Duration.between(c.start().toLocalDate().atTime(OPENING), c.start()).toMinutes();
            assertEquals(0, minutesSinceOpen % 60, "unaligned candidate start: " + c.start());
        }
    }
}
