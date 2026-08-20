package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDateTime;

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
