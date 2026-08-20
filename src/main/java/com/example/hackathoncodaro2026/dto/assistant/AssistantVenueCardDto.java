package com.example.hackathoncodaro2026.dto.assistant;

public record AssistantVenueCardDto(
        Long id,
        String name,
        String venueType,
        String venueTypeLabel,
        String address,
        String openingTime,
        String closingTime,
        int maxAttendees,
        String imagePath
) {
}
