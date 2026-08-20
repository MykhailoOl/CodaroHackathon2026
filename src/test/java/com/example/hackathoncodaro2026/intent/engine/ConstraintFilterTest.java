package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.Kind;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintFilterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 9, 0);

    private ResourceSlice resource(int capacity, Map<String, Object> attributes) {
        return new ResourceSlice(1L, "Slot A", "Site 1", "TYPE1", capacity,
                LocalTime.of(8, 0), LocalTime.of(20, 0), 60, attributes);
    }

    private IntentProperties config() {
        return new IntentProperties(15, 10, 3, 12, 0.25,
                new IntentProperties.Weights(20, 15, 15, 20, 15, 15),
                List.of(new ConstraintRule("req_quiet", Kind.HARD, true, "quiet requirement", 0,
                        "quiet", "yes", List.of())),
                Map.of());
    }

    private ScheduleSnapshot snapshot(List<ReservationSlice> reservations) {
        return new ScheduleSnapshot(List.of(), reservations, NOW, 42L, null);
    }

    @Test
    void rejectsCandidateThatStartsBeforeOrAtNow() {
        ResourceSlice r = resource(4, Map.of());
        Candidate c = new Candidate(1L, NOW, NOW.plusMinutes(60), new Interval(NOW.minusHours(1), NOW.plusHours(5)));

        assertFalse(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of(), 1));
    }

    @Test
    void admitsCandidateStrictlyAfterNow() {
        ResourceSlice r = resource(4, Map.of());
        LocalDateTime start = NOW.plusMinutes(1);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(NOW, NOW.plusHours(5)));

        assertTrue(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of(), 1));
    }

    @Test
    void rejectsCandidateOutsideOpeningHours() {
        ResourceSlice r = resource(4, Map.of());
        LocalDateTime start = NOW.toLocalDate().atTime(19, 30);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(start, start.plusHours(2)));

        assertFalse(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of(), 1));
    }

    @Test
    void rejectsCandidateWhenPartySizeExceedsCapacity() {
        ResourceSlice r = resource(2, Map.of());
        LocalDateTime start = NOW.plusMinutes(30);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(start, start.plusHours(2)));

        assertFalse(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of(), 5));
    }

    @Test
    void rejectsCandidateThatOverlapsAReservationUpToCapacity() {
        ResourceSlice r = resource(1, Map.of());
        LocalDateTime start = NOW.plusMinutes(30);
        LocalDateTime end = start.plusMinutes(60);
        ReservationSlice existing = new ReservationSlice(9L, 1L, 7L, start.minusMinutes(10), end.minusMinutes(10), 1);
        Candidate c = new Candidate(1L, start, end, new Interval(start, end));

        assertFalse(ConstraintFilter.admits(c, r, snapshot(List.of(existing)), config(), List.of(), 1));
    }

    @Test
    void admitsCandidateThatDoesNotOverlapAnyReservation() {
        ResourceSlice r = resource(1, Map.of());
        LocalDateTime start = NOW.plusMinutes(30);
        LocalDateTime end = start.plusMinutes(60);
        ReservationSlice existing = new ReservationSlice(9L, 1L, 7L, end.plusMinutes(5), end.plusMinutes(65), 1);
        Candidate c = new Candidate(1L, start, end, new Interval(start, end));

        assertTrue(ConstraintFilter.admits(c, r, snapshot(List.of(existing)), config(), List.of(), 1));
    }

    @Test
    void rejectsCandidateFailingAConfigHardConstraint() {
        ResourceSlice r = resource(4, Map.of("quiet", "no"));
        LocalDateTime start = NOW.plusMinutes(30);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(start, start.plusHours(2)));

        assertFalse(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of("req_quiet"), 1));
    }

    @Test
    void admitsCandidateWhenConfigHardConstraintIsSatisfied() {
        ResourceSlice r = resource(4, Map.of("quiet", "yes"));
        LocalDateTime start = NOW.plusMinutes(30);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(start, start.plusHours(2)));

        assertTrue(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of("req_quiet"), 1));
    }

    @Test
    void ignoresConfigHardConstraintWhenItIsNotActive() {
        ResourceSlice r = resource(4, Map.of("quiet", "no"));
        LocalDateTime start = NOW.plusMinutes(30);
        Candidate c = new Candidate(1L, start, start.plusMinutes(60), new Interval(start, start.plusHours(2)));

        assertTrue(ConstraintFilter.admits(c, r, snapshot(List.of()), config(), List.of(), 1));
    }
}
