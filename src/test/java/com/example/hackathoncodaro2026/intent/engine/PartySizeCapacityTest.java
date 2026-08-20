package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartySizeCapacityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 9, 0);

    private ResourceSlice court() {
        return new ResourceSlice(1L, "Tennis Court 1", "Site", "TYPE1",
                1, 2, 4,
                LocalTime.of(8, 0), LocalTime.of(22, 0), 60, Map.of());
    }

    private IntentProperties config() {
        return new IntentProperties(15, 10, 3, 12, 0.25,
                new IntentProperties.Weights(20, 15, 15, 20, 15, 15), List.of(), Map.of());
    }

    private ScheduleSnapshot snapshot() {
        return new ScheduleSnapshot(List.of(), List.of(), NOW, 42L, null);
    }

    private Candidate candidate() {
        LocalDateTime start = NOW.plusHours(2);
        return new Candidate(1L, start, start.plusMinutes(60),
                new Interval(NOW.plusHours(1), NOW.plusHours(8)));
    }

    @Test
    void admitsPartyLargerThanCapacityWhenWithinPartySizeRange() {
        assertTrue(ConstraintFilter.admits(candidate(), court(), snapshot(), config(), List.of(), 2),
                "a capacity-1 court must still accept a 2-person booking");
        assertTrue(ConstraintFilter.admits(candidate(), court(), snapshot(), config(), List.of(), 4),
                "party size up to maxPartySize must be admitted");
    }

    @Test
    void rejectsPartyLargerThanMaxPartySize() {
        assertFalse(ConstraintFilter.admits(candidate(), court(), snapshot(), config(), List.of(), 5),
                "party size above maxPartySize must be rejected");
    }

    @Test
    void admitsPartySmallerThanMinimum() {
        assertTrue(ConstraintFilter.admits(candidate(), court(), snapshot(), config(), List.of(), 1),
                "an unstated/small party must not eliminate every court");
    }
}
