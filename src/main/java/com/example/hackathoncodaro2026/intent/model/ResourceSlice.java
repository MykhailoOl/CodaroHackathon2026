package com.example.hackathoncodaro2026.intent.model;

import java.time.LocalTime;
import java.util.Map;

public record ResourceSlice(
        long id,
        String name,
        String facilityName,
        String typeKey,
        int capacity,
        int minPartySize,
        int maxPartySize,
        LocalTime opening,
        LocalTime closing,
        int slotDurationMinutes,
        Map<String, Object> attributes
) {
    public ResourceSlice {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        minPartySize = Math.max(1, minPartySize);
        maxPartySize = Math.max(minPartySize, maxPartySize);
    }

    public ResourceSlice(
            long id,
            String name,
            String facilityName,
            String typeKey,
            int capacity,
            LocalTime opening,
            LocalTime closing,
            int slotDurationMinutes,
            Map<String, Object> attributes
    ) {
        this(id, name, facilityName, typeKey, capacity, 1, Math.max(1, capacity),
                opening, closing, slotDurationMinutes, attributes);
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }
}
