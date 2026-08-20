package com.example.hackathoncodaro2026.intent.model;

import java.time.LocalDateTime;

public record ReservationSlice(
        long id,
        long resourceId,
        long userId,
        LocalDateTime start,
        LocalDateTime end,
        int occupancyUnits
) {
}
