package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties.ConstraintRule;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;

import java.util.List;
import java.util.Objects;

final class ConstraintFilter {

    private ConstraintFilter() {
    }

    static boolean admits(Candidate c, ResourceSlice resource, ScheduleSnapshot snapshot,
                           IntentProperties config, List<String> activeHardKeys, int partySize) {
        if (partySize > resource.maxPartySize()) {
            return false;
        }
        if (c.start().toLocalTime().isBefore(resource.opening())) {
            return false;
        }
        if (c.end().toLocalTime().isAfter(resource.closing())) {
            return false;
        }
        if (!c.start().isAfter(snapshot.now())) {
            return false;
        }
        if (overlapsAtCapacity(c, resource, snapshot)) {
            return false;
        }
        for (String key : activeHardKeys) {
            ConstraintRule rule = config.constraint(key);
            if (rule == null || !rule.isHard()) {
                continue;
            }
            Object value = resource.attribute(rule.attribute());
            if (value == null || !Objects.equals(String.valueOf(value), rule.equalsValue())) {
                return false;
            }
        }
        return true;
    }

    static List<Candidate> filter(List<Candidate> candidates, ResourceSlice resource, ScheduleSnapshot snapshot,
                                   IntentProperties config, List<String> activeHardKeys, int partySize) {
        return candidates.stream()
                .filter(c -> admits(c, resource, snapshot, config, activeHardKeys, partySize))
                .toList();
    }

    private static boolean overlapsAtCapacity(Candidate c, ResourceSlice resource, ScheduleSnapshot snapshot) {
        int sum = 0;
        for (ReservationSlice r : snapshot.reservationsFor(resource.id())) {
            if (r.start().isBefore(c.end()) && c.start().isBefore(r.end())) {
                sum += r.occupancyUnits();
            }
        }
        return sum >= resource.capacity();
    }
}
