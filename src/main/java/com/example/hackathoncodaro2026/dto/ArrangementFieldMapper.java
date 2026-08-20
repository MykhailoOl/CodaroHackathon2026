package com.example.hackathoncodaro2026.dto;

public final class ArrangementFieldMapper {

    private ArrangementFieldMapper() {
    }

    public static String stepFor(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        return switch (field) {
            case "homeId" -> "home";
            case "venueId" -> "venue";
            case "serviceType" -> "service";
            case "funeralPackage" -> "pack";
            case "deceasedFullName", "dateOfDeath", "dateOfBirth" -> "deceased";
            case "attendees" -> "attendees";
            case "phone" -> "phone";
            case "extraIds" -> "extras";
            case "paymentMethod" -> "payment";
            case "note" -> "note";
            default -> null;
        };
    }
}
