package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDateTime;

/**
 * One arrangement as the family's channel needs to show it back to them: where, when,
 * whether it still stands, and the reference to quote when they call the funeral home.
 */
public record ArrangementDto(
        Long reservationId,
        String resourceName,
        String facilityName,
        LocalDateTime start,
        LocalDateTime end,
        String status,
        String totalAmount
) {
}
