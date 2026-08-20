package com.example.hackathoncodaro2026.dto.assistant;

import java.util.List;

public record AssistantVenueDetailDto(
        Long id,
        Long homeId,
        String homeName,
        String name,
        String venueType,
        String venueTypeLabel,
        String address,
        String openingTime,
        String closingTime,
        int maxAttendees,
        String imagePath,
        List<AssistantServiceOptionDto> serviceTypes,
        List<AssistantPackageDto> packages
) {
}
