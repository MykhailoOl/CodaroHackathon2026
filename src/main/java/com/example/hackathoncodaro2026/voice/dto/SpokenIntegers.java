package com.example.hackathoncodaro2026.voice.dto;

import java.util.Locale;
import java.util.Map;

final class SpokenIntegers {

    private static final Map<String, Integer> WORDS = Map.of(
            "one", 1,
            "two", 2,
            "three", 3,
            "four", 4,
            "five", 5,
            "six", 6,
            "seven", 7,
            "eight", 8,
            "nine", 9,
            "ten", 10
    );

    private SpokenIntegers() {
    }

    static Integer parse(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed < 1 ? null : parsed;
        }
        String raw = value.toString();
        if (raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        Integer word = WORDS.get(normalized);
        if (word != null) {
            return word;
        }
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed < 1 ? null : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed < 1 ? null : parsed;
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            return parsed < 1 ? null : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
