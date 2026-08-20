package com.example.hackathoncodaro2026.model.enums;

import java.math.BigDecimal;

/**
 * The services a funeral home can be booked for.
 *
 * <p>Two shapes, and the split is load-bearing — reservations, pricing and the occupancy
 * views all branch on it. {@code CHAPEL}, {@code BURIAL} and {@code RECEPTION} are
 * <em>attended</em>: they always carry a mourner count. The rest are booked one at a
 * time ({@code minPartySize == maxPartySize == 1}), which makes them offer a booking
 * mode: a direct cremation, a plain transfer, a private viewing — or the attended
 * variant, where the family is present.
 *
 * <p>The {@code lesson*} fields keep their names because pricing, reservations and the
 * templates all read them, but they now mean "with the family present and an officiant
 * attached" rather than "coached session": {@code lessonHourlyPrice} is the rate when a
 * celebrant leads it, and the lesson party range is the attendance that variant allows.
 */
public enum ResourceType {
    CHAPEL("Chapel ceremony", "/images/services/chapel.svg", 2, 200, 2, 200, new BigDecimal("900.00"), new BigDecimal("1200.00")),
    BURIAL("Burial", "/images/services/burial.svg", 2, 120, 2, 120, new BigDecimal("2400.00"), new BigDecimal("2800.00")),
    RECEPTION("Wake reception", "/images/services/reception.svg", 2, 150, 2, 150, new BigDecimal("600.00"), new BigDecimal("750.00")),
    CREMATION("Cremation", "/images/services/cremation.svg", 2, 60, 2, 60, new BigDecimal("1600.00"), new BigDecimal("1900.00")),
    TRANSPORT("Hearse transport", "/images/services/transport.svg", 1, 1, 2, 8, new BigDecimal("450.00"), new BigDecimal("550.00")),
    VIEWING("Viewing room", "/images/services/viewing.svg", 1, 1, 2, 30, new BigDecimal("300.00"), new BigDecimal("380.00")),
    REPATRIATION("Repatriation", "/images/services/repatriation.svg", 1, 1, 2, 6, new BigDecimal("2100.00"), new BigDecimal("2500.00"));

    private final String displayName;
    private final String imagePath;
    private final int minPartySize;
    private final int maxPartySize;
    private final int lessonMinPartySize;
    private final int lessonMaxPartySize;
    private final BigDecimal baseHourlyPrice;
    private final BigDecimal lessonHourlyPrice;

    ResourceType(
            String displayName,
            String imagePath,
            int minPartySize,
            int maxPartySize,
            int lessonMinPartySize,
            int lessonMaxPartySize,
            BigDecimal baseHourlyPrice,
            BigDecimal lessonHourlyPrice
    ) {
        this.displayName = displayName;
        this.imagePath = imagePath;
        this.minPartySize = minPartySize;
        this.maxPartySize = maxPartySize;
        this.lessonMinPartySize = lessonMinPartySize;
        this.lessonMaxPartySize = lessonMaxPartySize;
        this.baseHourlyPrice = baseHourlyPrice;
        this.lessonHourlyPrice = lessonHourlyPrice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public int getMinPartySize() {
        return minPartySize;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getLessonMinPartySize() {
        return lessonMinPartySize;
    }

    public int getLessonMaxPartySize() {
        return lessonMaxPartySize;
    }

    public BigDecimal getBaseHourlyPrice() {
        return baseHourlyPrice;
    }

    public BigDecimal getLessonHourlyPrice() {
        return lessonHourlyPrice;
    }
}
