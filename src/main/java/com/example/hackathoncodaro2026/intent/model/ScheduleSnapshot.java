package com.example.hackathoncodaro2026.intent.model;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleSnapshot(
        List<ResourceSlice> resources,
        List<ReservationSlice> reservations,
        LocalDateTime now,
        long requestingUserId,
        UserPrefs prefs
) {
    public ScheduleSnapshot {
        resources = resources == null ? List.of() : List.copyOf(resources);
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
        prefs = prefs == null ? UserPrefs.none() : prefs;
    }

    public List<ReservationSlice> reservationsFor(long resourceId) {
        return reservations.stream().filter(r -> r.resourceId() == resourceId).toList();
    }
}
