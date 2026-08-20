package com.example.hackathoncodaro2026.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ServiceType {
    BURIAL_CEREMONY("Burial ceremony", EnumSet.of(VenueType.CHAPEL, VenueType.CEREMONY_HALL, VenueType.MEMORIAL_GARDEN)),
    CREMATION_CEREMONY("Cremation ceremony", EnumSet.of(VenueType.CREMATORIUM, VenueType.CHAPEL)),
    MEMORIAL_SERVICE("Memorial service", EnumSet.of(
            VenueType.CHAPEL, VenueType.CEREMONY_HALL, VenueType.MEMORIAL_GARDEN, VenueType.RECEPTION_HALL
    )),
    FAREWELL_CEREMONY("Farewell ceremony", EnumSet.of(VenueType.CHAPEL, VenueType.CEREMONY_HALL, VenueType.MEMORIAL_GARDEN));

    private final String label;
    private final Set<VenueType> venues;

    ServiceType(String label, Set<VenueType> venues) {
        this.label = label;
        this.venues = venues;
    }

    public String getLabel() {
        return label;
    }

    public boolean allows(VenueType type) {
        return type != null && venues.contains(type);
    }
}
