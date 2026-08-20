package com.example.hackathoncodaro2026.intent.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class Intervals {

    private Intervals() {
    }

    static List<Interval> merge(List<Interval> raw) {
        List<Interval> sorted = raw.stream()
                .filter(i -> !i.isEmpty())
                .sorted()
                .toList();
        List<Interval> merged = new ArrayList<>();
        for (Interval iv : sorted) {
            if (merged.isEmpty()) {
                merged.add(iv);
                continue;
            }
            Interval last = merged.get(merged.size() - 1);
            if (!iv.start().isAfter(last.end())) {
                LocalDateTime newEnd = iv.end().isAfter(last.end()) ? iv.end() : last.end();
                merged.set(merged.size() - 1, new Interval(last.start(), newEnd));
            } else {
                merged.add(iv);
            }
        }
        return merged;
    }

    static List<Interval> subtract(Interval window, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        LocalDateTime cursor = window.start();
        for (Interval b : merge(busy)) {
            Interval clipped = new Interval(
                    b.start().isBefore(window.start()) ? window.start() : b.start(),
                    b.end().isAfter(window.end()) ? window.end() : b.end());
            if (clipped.isEmpty()) {
                continue;
            }
            if (!clipped.start().isBefore(window.end()) || !clipped.end().isAfter(window.start())) {
                continue;
            }
            if (clipped.start().isAfter(cursor)) {
                free.add(new Interval(cursor, clipped.start()));
            }
            if (clipped.end().isAfter(cursor)) {
                cursor = clipped.end();
            }
        }
        if (cursor.isBefore(window.end())) {
            free.add(new Interval(cursor, window.end()));
        }
        return free;
    }
}
