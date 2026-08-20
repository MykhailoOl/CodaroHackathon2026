package com.example.hackathoncodaro2026.dto.telegram;

import java.time.LocalDateTime;

public record TelegramHistoryItem(
        Long reservationId,
        String venueName,
        String homeName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        String formattedAmount,
        int attendees
) {
}
