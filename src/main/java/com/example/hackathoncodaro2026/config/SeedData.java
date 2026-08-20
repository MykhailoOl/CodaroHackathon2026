package com.example.hackathoncodaro2026.config;

import com.example.hackathoncodaro2026.model.enums.ResourceType;

import java.math.BigDecimal;
import java.util.List;

public record SeedData(
        List<SeedFacility> facilities,
        List<SeedInventoryItem> inventory
) {

    public record SeedFacility(
            String name,
            String description,
            String phone,
            SeedAddress address,
            ResourceType coverType,
            List<SeedResource> resources
    ) {
    }

    public record SeedResource(
            String name,
            ResourceType type,
            SeedAddress address,
            int capacity
    ) {
    }

    public record SeedInventoryItem(
            String name,
            BigDecimal price,
            ResourceType type
    ) {
    }

    public record SeedAddress(
            String street,
            String buildingNumber,
            String postalCode,
            String district
    ) {
    }
}