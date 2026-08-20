package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.Kind;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RelaxStep.Action;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelaxerTest {

    private IntentProperties config() {
        return new IntentProperties(15, 10, 3, 12, 0.20,
                new IntentProperties.Weights(20, 15, 15, 20, 15, 15),
                List.of(
                        new ConstraintRule("soft_low", Kind.SOFT, false, "low weight preference", 3, "a", "yes",
                                List.of()),
                        new ConstraintRule("soft_high", Kind.SOFT, false, "high weight preference", 9, "b", "yes",
                                List.of()),
                        new ConstraintRule("hard_relaxable", Kind.HARD, true, "relaxable requirement", 0, "c", "yes",
                                List.of()),
                        new ConstraintRule("hard_locked", Kind.HARD, false, "locked requirement", 0, "d", "yes",
                                List.of())),
                Map.of());
    }

    @Test
    void ordersRungsWidenThenSoftLowestWeightFirstThenRelaxableHardThenShrink() {
        LocalDate day = LocalDate.of(2026, 8, 25);
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY,
                List.of("hard_relaxable", "hard_locked"), List.of("soft_low", "soft_high"), "TYPE1", 1);

        List<Relaxer.Rung> rungs = Relaxer.ladder(spec, config());

        assertEquals(Action.WIDEN_DAY_WINDOW, rungs.get(0).step().action());
        assertEquals(Action.DROP_SOFT_CONSTRAINT, rungs.get(1).step().action());
        assertEquals(List.of("soft_low"), rungs.get(1).step().droppedKeys());
        assertEquals(Action.DROP_SOFT_CONSTRAINT, rungs.get(2).step().action());
        assertEquals(List.of("soft_high"), rungs.get(2).step().droppedKeys());
        assertEquals(Action.DROP_HARD_CONSTRAINT, rungs.get(3).step().action());
        assertEquals(List.of("hard_relaxable"), rungs.get(3).step().droppedKeys());

        boolean lockedEverDropped = rungs.stream()
                .anyMatch(r -> r.step().action() == Action.DROP_HARD_CONSTRAINT
                        && r.step().droppedKeys().contains("hard_locked"));
        assertFalse(lockedEverDropped);
        assertTrue(rungs.get(3).state().hardKeys().contains("hard_locked"));

        List<Relaxer.Rung> shrinkRungs = rungs.subList(4, rungs.size());
        assertFalse(shrinkRungs.isEmpty());
        for (Relaxer.Rung r : shrinkRungs) {
            assertEquals(Action.SHRINK_DURATION, r.step().action());
            assertTrue(r.state().durationMin() < spec.durationMin());
            assertTrue(r.state().durationMin() >= Math.round(spec.durationMin() * (1 - config().shrinkDurationMaxPct())));
        }
    }

    @Test
    void everyRungAccumulatesRelaxedKeysFromEarlierRungs() {
        LocalDate day = LocalDate.of(2026, 8, 25);
        IntentSpec spec = new IntentSpec(60, day, day, TimeOfDay.ANY,
                List.of("hard_relaxable"), List.of("soft_low"), "TYPE1", 1);

        List<Relaxer.Rung> rungs = Relaxer.ladder(spec, config());
        Relaxer.Rung last = rungs.get(rungs.size() - 1);

        assertTrue(last.state().relaxedKeys().contains("day_window"));
        assertTrue(last.state().relaxedKeys().contains("soft_low"));
        assertTrue(last.state().relaxedKeys().contains("hard_relaxable"));
        assertTrue(last.state().relaxedKeys().contains("duration"));
    }
}
