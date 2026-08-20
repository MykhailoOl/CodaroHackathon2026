package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.Weights;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import com.example.hackathoncodaro2026.intent.model.ScoreTerm;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import com.example.hackathoncodaro2026.intent.model.UserPrefs;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class Scorer {

    static final String TIME_OF_DAY_KEY = "time_of_day";
    static final String DAY_PROXIMITY_KEY = "day_proximity";
    static final String BUFFER_KEY = "buffer";
    static final String FRAGMENTATION_KEY = "fragmentation";
    static final String RESOURCE_LOAD_KEY = "resource_load";
    static final String WORKLOAD_KEY = "workload";

    private Scorer() {
    }

    static List<ScoreTerm> score(Candidate c, ResourceSlice resource, IntentSpec spec, ScheduleSnapshot snapshot,
                                  IntentProperties config, List<String> activeSoftKeys,
                                  LocalDate effectiveDayFrom, LocalDate effectiveDayTo) {
        Weights w = config.weights();
        List<ScoreTerm> terms = new ArrayList<>();
        terms.add(timeOfDay(c, spec, snapshot, w.timeOfDay()));
        terms.add(dayProximity(c, effectiveDayFrom, effectiveDayTo, w.dayProximity()));
        terms.add(buffer(c, snapshot, config, w.buffer()));
        terms.add(fragmentation(c, spec, w.fragmentation()));
        terms.add(resourceLoad(resource, snapshot, effectiveDayFrom, effectiveDayTo, w.resourceLoad()));
        terms.add(workload(c, snapshot, w.workload()));

        for (String key : activeSoftKeys) {
            ConstraintRule rule = config.constraint(key);
            if (rule == null || rule.isHard()) {
                continue;
            }
            boolean matched = matches(resource, rule);
            double delta = matched ? rule.weight() : -rule.weight() * 0.5;
            terms.add(new ScoreTerm(rule.key(), rule.label(), delta, matched));
        }
        return terms;
    }

    private static boolean matches(ResourceSlice resource, ConstraintRule rule) {
        Object value = resource.attribute(rule.attribute());
        return value != null && Objects.equals(String.valueOf(value), rule.equalsValue());
    }

    private static ScoreTerm timeOfDay(Candidate c, IntentSpec spec, ScheduleSnapshot snapshot, double weight) {
        int fromMin;
        int toMin;
        UserPrefs prefs = snapshot.prefs();
        if (prefs.hasPreferredWindow()) {
            fromMin = prefs.preferredFromMin();
            toMin = prefs.preferredToMin();
        } else {
            TimeOfDay tod = spec.timeOfDay();
            fromMin = tod.fromMin();
            toMin = tod.toMin();
        }
        int startMin = c.start().getHour() * 60 + c.start().getMinute();
        int distance;
        if (startMin >= fromMin && startMin <= toMin) {
            distance = 0;
        } else {
            distance = Math.min(Math.abs(startMin - fromMin), Math.abs(startMin - toMin));
        }
        double fraction = 1.0 - Math.min(1.0, distance / 180.0) * 2.0;
        return new ScoreTerm(TIME_OF_DAY_KEY, "preferred time of day", weight * fraction, distance == 0);
    }

    private static ScoreTerm dayProximity(Candidate c, LocalDate dayFrom, LocalDate dayTo, double weight) {
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(dayFrom, dayTo));
        long offset = ChronoUnit.DAYS.between(dayFrom, c.start().toLocalDate());
        long clamped = Math.max(0, Math.min(offset, totalDays));
        double fraction = 1.0 - 2.0 * (clamped / (double) totalDays);
        return new ScoreTerm(DAY_PROXIMITY_KEY, "earlier availability", weight * fraction, offset == 0);
    }

    private static ScoreTerm buffer(Candidate c, ScheduleSnapshot snapshot, IntentProperties config, double weight) {
        long gapBefore = Duration.between(c.sourceInterval().start(), c.start()).toMinutes();
        long gapAfter = Duration.between(c.end(), c.sourceInterval().end()).toMinutes();
        long minGap = Math.min(gapBefore, gapAfter);
        int target = Math.max(config.bufferMin(), snapshot.prefs().minBufferMin());
        double fraction;
        boolean satisfied;
        if (target <= 0) {
            fraction = 1.0;
            satisfied = true;
        } else {
            fraction = Math.max(-1.0, Math.min(1.0, (minGap - (double) target) / target));
            satisfied = minGap >= target;
        }
        return new ScoreTerm(BUFFER_KEY, "buffer around booking", weight * fraction, satisfied);
    }

    private static ScoreTerm fragmentation(Candidate c, IntentSpec spec, double weight) {
        int duration = spec.durationMin();
        long remnantBefore = Duration.between(c.sourceInterval().start(), c.start()).toMinutes();
        long remnantAfter = Duration.between(c.end(), c.sourceInterval().end()).toMinutes();
        boolean badBefore = remnantBefore > 0 && remnantBefore < duration;
        boolean badAfter = remnantAfter > 0 && remnantAfter < duration;
        int badCount = (badBefore ? 1 : 0) + (badAfter ? 1 : 0);
        double fraction = 1.0 - badCount;
        return new ScoreTerm(FRAGMENTATION_KEY, "avoids fragmenting availability", weight * fraction, badCount == 0);
    }

    private static ScoreTerm resourceLoad(ResourceSlice resource, ScheduleSnapshot snapshot,
                                           LocalDate dayFrom, LocalDate dayTo, double weight) {
        long busyMinutes = 0;
        long openMinutes = 0;
        LocalDate d = dayFrom;
        while (!d.isAfter(dayTo)) {
            if (resource.closing().isAfter(resource.opening())) {
                LocalDateTime ws = d.atTime(resource.opening());
                LocalDateTime we = d.atTime(resource.closing());
                openMinutes += Duration.between(ws, we).toMinutes();
                for (ReservationSlice r : snapshot.reservationsFor(resource.id())) {
                    LocalDateTime s = r.start().isBefore(ws) ? ws : r.start();
                    LocalDateTime e = r.end().isAfter(we) ? we : r.end();
                    if (e.isAfter(s)) {
                        busyMinutes += Duration.between(s, e).toMinutes();
                    }
                }
            }
            d = d.plusDays(1);
        }
        double load = openMinutes <= 0 ? 0.0 : Math.min(1.0, busyMinutes / (double) openMinutes);
        double fraction = 1.0 - 2.0 * load;
        return new ScoreTerm(RESOURCE_LOAD_KEY, "resource availability", weight * fraction, load < 0.5);
    }

    private static ScoreTerm workload(Candidate c, ScheduleSnapshot snapshot, double weight) {
        LocalDate day = c.start().toLocalDate();
        long userId = snapshot.requestingUserId();
        int count = 0;
        boolean backToBack = false;
        for (ReservationSlice r : snapshot.reservations()) {
            if (r.userId() != userId || !r.start().toLocalDate().isEqual(day)) {
                continue;
            }
            count++;
            if (r.end().isEqual(c.start()) || r.start().isEqual(c.end())) {
                backToBack = true;
            }
        }
        double fraction = Math.max(-1.0, -Math.min(1.0, count * 0.34) - (backToBack ? 0.3 : 0.0));
        return new ScoreTerm(WORKLOAD_KEY, "workload balance", weight * fraction, count == 0);
    }
}
