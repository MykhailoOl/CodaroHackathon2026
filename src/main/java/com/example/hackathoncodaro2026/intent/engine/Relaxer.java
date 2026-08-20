package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RelaxStep;
import com.example.hackathoncodaro2026.intent.model.RelaxStep.Action;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

final class Relaxer {

    private static final double SHRINK_STEP_PCT = 0.10;

    private Relaxer() {
    }

    record State(LocalDate dayFrom, LocalDate dayTo, List<String> hardKeys, List<String> softKeys,
                  int durationMin, List<String> relaxedKeys) {
    }

    record Rung(State state, RelaxStep step) {
    }

    static State initial(IntentSpec spec) {
        return new State(spec.dayFrom(), spec.dayTo(), spec.hardConstraints(), spec.softConstraints(),
                spec.durationMin(), List.of());
    }

    static List<Rung> ladder(IntentSpec spec, IntentProperties config) {
        List<Rung> rungs = new ArrayList<>();
        State state = initial(spec);

        LocalDate widenedFrom = state.dayFrom().minusDays(2);
        LocalDate widenedTo = state.dayTo().plusDays(2);
        state = new State(widenedFrom, widenedTo, state.hardKeys(), state.softKeys(), state.durationMin(),
                withKey(state.relaxedKeys(), "day_window"));
        rungs.add(new Rung(state, new RelaxStep(Action.WIDEN_DAY_WINDOW,
                "widened day window to " + widenedFrom + ".." + widenedTo, List.of("day_window"))));

        List<String> softOrder = state.softKeys().stream()
                .filter(k -> config.constraint(k) != null && !config.constraint(k).isHard())
                .sorted(Comparator.comparingDouble(k -> config.constraint(k).weight()))
                .toList();
        List<String> remainingSoft = new ArrayList<>(state.softKeys());
        for (String key : softOrder) {
            remainingSoft.remove(key);
            ConstraintRule rule = config.constraint(key);
            state = new State(state.dayFrom(), state.dayTo(), state.hardKeys(), List.copyOf(remainingSoft),
                    state.durationMin(), withKey(state.relaxedKeys(), key));
            rungs.add(new Rung(state, new RelaxStep(Action.DROP_SOFT_CONSTRAINT,
                    "dropped soft constraint " + (rule == null ? key : rule.label()), List.of(key))));
        }

        List<String> hardOrder = state.hardKeys().stream()
                .filter(k -> config.constraint(k) != null && config.constraint(k).isHard()
                        && config.constraint(k).relaxable())
                .toList();
        List<String> remainingHard = new ArrayList<>(state.hardKeys());
        for (String key : hardOrder) {
            remainingHard.remove(key);
            ConstraintRule rule = config.constraint(key);
            state = new State(state.dayFrom(), state.dayTo(), List.copyOf(remainingHard), state.softKeys(),
                    state.durationMin(), withKey(state.relaxedKeys(), key));
            rungs.add(new Rung(state, new RelaxStep(Action.DROP_HARD_CONSTRAINT,
                    "dropped hard constraint " + (rule == null ? key : rule.label()), List.of(key))));
        }

        double maxPct = Math.max(0.0, config.shrinkDurationMaxPct());
        int originalDuration = spec.durationMin();
        double pct = 0.0;
        while (pct < maxPct - 1e-9) {
            pct = Math.min(maxPct, pct + SHRINK_STEP_PCT);
            int shrunk = Math.max(1, (int) Math.round(originalDuration * (1.0 - pct)));
            state = new State(state.dayFrom(), state.dayTo(), state.hardKeys(), state.softKeys(), shrunk,
                    withKey(state.relaxedKeys(), "duration"));
            rungs.add(new Rung(state, new RelaxStep(Action.SHRINK_DURATION,
                    "shrunk duration to " + shrunk + " minutes (-" + Math.round(pct * 100) + "%)",
                    List.of("duration"))));
        }

        return rungs;
    }

    private static List<String> withKey(List<String> existing, String key) {
        LinkedHashSet<String> set = new LinkedHashSet<>(existing);
        set.add(key);
        return List.copyOf(set);
    }
}
