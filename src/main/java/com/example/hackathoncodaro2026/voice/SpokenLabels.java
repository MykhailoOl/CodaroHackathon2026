package com.example.hackathoncodaro2026.voice;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

final class SpokenLabels {

    private static final String[] HOURS = {
            "twelve", "one", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "eleven"
    };

    private static final Map<String, String> HOMES = Map.ofEntries(
            Map.entry("EverRest Warsaw", "EverRest Warsaw"),
            Map.entry("Peaceful Passage", "Peaceful Passage"),
            Map.entry("Warsaw Memorial Gardens", "Warsaw Memorial Gardens"),
            Map.entry("Serenity Farewell House", "Serenity Farewell House"),
            Map.entry("Quiet Harbor House", "Quiet Harbor House"),
            Map.entry("Linden Rest Chapel", "Linden Rest Chapel"),
            Map.entry("Dawn Remembrance", "Dawn Remembrance")
    );

    private SpokenLabels() {
    }

    static String slot(String venueName, String homeName, LocalDateTime start, LocalDateTime end) {
        String venue = venueName == null || venueName.isBlank() ? "chapel" : venueName.trim();
        String home = spokenHome(homeName);
        if (!home.isBlank()) {
            venue = venue + " at " + home;
        }
        String day = start.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK);
        return venue + ", " + clock(start.toLocalTime()) + " to " + clock(end.toLocalTime()) + " " + day;
    }

    static String clock(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour >= 12 ? "PM" : "AM";
        String hourWord = HOURS[hour % 12];
        if (minute == 0) {
            return hourWord + " " + period;
        }
        if (minute == 30) {
            return hourWord + " thirty " + period;
        }
        return hourWord + " " + minute + " " + period;
    }

    static String spokenHome(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return HOMES.getOrDefault(name, name);
    }
}
