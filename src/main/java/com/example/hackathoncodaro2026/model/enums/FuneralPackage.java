package com.example.hackathoncodaro2026.model.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum FuneralPackage {
    ESSENTIAL("Essential", "Quiet gathering with a simple ceremony and family seating.", 90, "2400.00"),
    CLASSIC("Classic", "Full ceremony with printed programmes and a reception window.", 120, "4200.00"),
    TRIBUTE("Tribute", "Extended remembrance with music, flowers, and a longer gathering.", 180, "6800.00");

    private final String label;
    private final String description;
    private final int durationMinutes;
    private final BigDecimal basePrice;

    FuneralPackage(String label, String description, int durationMinutes, String basePrice) {
        this.label = label;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.basePrice = new BigDecimal(basePrice).setScale(2, RoundingMode.HALF_UP);
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }
}
