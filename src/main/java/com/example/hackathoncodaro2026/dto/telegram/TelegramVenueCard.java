package com.example.hackathoncodaro2026.dto.telegram;

public record TelegramVenueCard(
        Long id,
        String name,
        String homeName,
        String venueTypeLabel,
        int maxAttendees,
        String address
) {
}
