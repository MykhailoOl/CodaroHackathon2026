package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grid-free candidate slot generation. Busy intervals are subtracted from
 * each day's open window to get free structure; candidate starts are placed
 * at structural anchors inside that free structure (never on a fixed clock),
 * then backfilled at the configured granularity up to a per-interval cap.
 */
final class CandidateGenerator {

    private CandidateGenerator() {
    }

    static List<Candidate> generate(ResourceSlice resource, IntentSpec spec, ScheduleSnapshot snapshot,
                                      IntentProperties config, LocalDate dayFrom, LocalDate dayTo) {
        List<Candidate> out = new ArrayList<>();
        if (resource.opening() == null || resource.closing() == null
                || !resource.closing().isAfter(resource.opening())) {
            return out;
        }
        List<ReservationSlice> reservations = snapshot.reservationsFor(resource.id());
        List<Interval> busyTemplate = new ArrayList<>();
        for (ReservationSlice r : reservations) {
            busyTemplate.add(new Interval(r.start(), r.end()));
        }

        LocalDate d = dayFrom;
        while (!d.isAfter(dayTo)) {
            LocalDateTime windowStart = d.atTime(resource.opening());
            LocalDateTime windowEnd = d.atTime(resource.closing());

            if (windowStart.isBefore(snapshot.now())) {
                if (snapshot.now().isBefore(windowEnd)) {
                    windowStart = snapshot.now();
                } else {
                    d = d.plusDays(1);
                    continue;
                }
            }
            if (!windowStart.isBefore(windowEnd)) {
                d = d.plusDays(1);
                continue;
            }

            Interval window = new Interval(windowStart, windowEnd);
            List<Interval> free = Intervals.subtract(window, busyTemplate);
            for (Interval fi : free) {
                out.addAll(candidatesInFreeInterval(resource, spec, config, fi));
            }
            d = d.plusDays(1);
        }
        return out;
    }

    private static List<Candidate> candidatesInFreeInterval(ResourceSlice resource, IntentSpec spec,
                                                              IntentProperties config, Interval fi) {
        int duration = spec.durationMin();
        if (fi.minutes() < duration) {
            return List.of();
        }
        LocalDateTime latestStart = fi.end().minusMinutes(duration);

        LinkedHashSet<LocalDateTime> starts = new LinkedHashSet<>();
        addIfInRange(starts, alignUp(resource, fi.start()), fi.start(), latestStart);
        addIfInRange(starts, alignUp(resource, fi.start().plusMinutes(config.bufferMin())), fi.start(), latestStart);

        LocalDate day = fi.start().toLocalDate();
        LocalDateTime todFrom = day.atStartOfDay().plusMinutes(spec.timeOfDay().fromMin());
        LocalDateTime todTo = day.atStartOfDay().plusMinutes(spec.timeOfDay().toMin());
        addIfInRange(starts, alignUp(resource, clamp(todFrom, fi.start(), latestStart)), fi.start(), latestStart);
        addIfInRange(starts, alignDown(resource, clamp(todTo.minusMinutes(duration), fi.start(), latestStart)),
                fi.start(), latestStart);

        addIfInRange(starts, alignDown(resource, latestStart), fi.start(), latestStart);

        int cap = Math.max(1, config.maxCandidatesPerInterval());
        LocalDateTime cursor = fi.start();
        while (starts.size() < cap && !cursor.isAfter(latestStart)) {
            addIfInRange(starts, alignUp(resource, cursor), fi.start(), latestStart);
            cursor = cursor.plusMinutes(Math.max(1, config.granularityMin()));
        }

        List<LocalDateTime> ordered = new ArrayList<>(starts);
        ordered.sort(Comparator.naturalOrder());

        List<Candidate> result = new ArrayList<>();
        int limit = Math.min(cap, ordered.size());
        for (int i = 0; i < limit; i++) {
            LocalDateTime s = ordered.get(i);
            result.add(new Candidate(resource.id(), s, s.plusMinutes(duration), fi));
        }
        return result;
    }

    private static void addIfInRange(Set<LocalDateTime> set, LocalDateTime t, LocalDateTime lo, LocalDateTime hi) {
        if (!t.isBefore(lo) && !t.isAfter(hi)) {
            set.add(t);
        }
    }

    private static LocalDateTime clamp(LocalDateTime t, LocalDateTime lo, LocalDateTime hi) {
        if (t.isBefore(lo)) {
            return lo;
        }
        if (t.isAfter(hi)) {
            return hi;
        }
        return t;
    }

    /**
     * Rounds {@code target} to the nearest slot boundary the resource actually
     * accepts bookings on (opening time plus whole multiples of
     * {@code slotDurationMinutes} — the same rule {@code ReservationServiceImpl.isAligned}
     * enforces at confirm time), moving forward so the candidate never lands before
     * the free interval it came from.
     */
    private static LocalDateTime alignUp(ResourceSlice resource, LocalDateTime target) {
        LocalDateTime dayOpen = target.toLocalDate().atTime(resource.opening());
        long minutesSinceOpen = Duration.between(dayOpen, target).toMinutes();
        if (minutesSinceOpen <= 0) {
            return dayOpen;
        }
        int slot = resource.slotDurationMinutes();
        long remainder = minutesSinceOpen % slot;
        return remainder == 0 ? target : target.plusMinutes(slot - remainder);
    }

    /** Same grid as {@link #alignUp}, but rounds backward. */
    private static LocalDateTime alignDown(ResourceSlice resource, LocalDateTime target) {
        LocalDateTime dayOpen = target.toLocalDate().atTime(resource.opening());
        long minutesSinceOpen = Duration.between(dayOpen, target).toMinutes();
        if (minutesSinceOpen <= 0) {
            return dayOpen;
        }
        int slot = resource.slotDurationMinutes();
        return target.minusMinutes(minutesSinceOpen % slot);
    }
}
