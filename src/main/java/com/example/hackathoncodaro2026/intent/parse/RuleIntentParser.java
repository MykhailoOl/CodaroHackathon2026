package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.TimeOfDay;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleIntentParser implements IntentParser {

    private static final int DEFAULT_DURATION_MIN = 60;
    private static final int DEFAULT_WINDOW_DAYS = 7;

    private final IntentProperties config;
    private final List<String[]> synonyms;

    public RuleIntentParser(IntentProperties config, DomainProperties domain) {
        this.config = config;
        this.synonyms = domain.synonymPairs();
    }

    @Override
    public ParseResult parse(String text, LocalDate today, int partySize) {
        String raw = text == null ? "" : text;
        String lower = raw.toLowerCase(Locale.ROOT);

        if (today == null) {
            today = LocalDate.now();
        }

        int duration = parseDuration(lower);
        DayWindow window = parseDayWindow(lower, today);
        TimeOfDay timeOfDay = parseTimeOfDay(lower);
        String resourceType = parseResourceType(lower);
        int party = parsePartySize(lower, partySize);

        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        if (config.constraints() != null) {
            for (IntentProperties.ConstraintRule rule : config.constraints()) {
                if (rule == null || rule.key() == null) {
                    continue;
                }
                if (matchesAnyPhrase(lower, rule.phrases())) {
                    if (rule.isHard()) {
                        hard.add(rule.key());
                    } else {
                        soft.add(rule.key());
                    }
                }
            }
        }

        IntentSpec spec = new IntentSpec(
                duration,
                window.from(),
                window.to(),
                timeOfDay,
                hard,
                soft,
                resourceType,
                party
        );

        spec = validate(spec, today);

        return new ParseResult(spec, "rules");
    }

    private IntentSpec validate(IntentSpec spec, LocalDate today) {
        int duration = spec.durationMin() > 0 ? spec.durationMin() : DEFAULT_DURATION_MIN;

        LocalDate from = spec.dayFrom();
        LocalDate to = spec.dayTo();
        if (from == null || to == null || from.isAfter(to)) {
            from = today;
            to = today.plusDays(DEFAULT_WINDOW_DAYS);
        }

        Set<String> validKeys = new LinkedHashSet<>();
        if (config.constraints() != null) {
            for (IntentProperties.ConstraintRule rule : config.constraints()) {
                if (rule != null && rule.key() != null) {
                    validKeys.add(rule.key());
                }
            }
        }
        List<String> hard = spec.hardConstraints().stream().filter(validKeys::contains).toList();
        List<String> soft = spec.softConstraints().stream()
                .filter(validKeys::contains)
                .filter(k -> !hard.contains(k))
                .toList();

        return new IntentSpec(duration, from, to, spec.timeOfDay(), hard, soft, spec.resourceType(), spec.partySize());
    }

    private boolean matchesAnyPhrase(String lower, List<String> phrases) {
        if (phrases == null) {
            return false;
        }
        for (String phrase : phrases) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            if (containsWordish(lower, phrase.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWordish(String haystack, String needle) {
        if (needle.isBlank()) {
            return false;
        }
        String pattern = "(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])";
        return Pattern.compile(pattern).matcher(haystack).find();
    }


    private static final Pattern HOURS_MIN_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hrs|hour|hours)\\b");
    private static final Pattern MINUTES_PATTERN =
            Pattern.compile("(\\d+)\\s*(?:m|min|mins|minute|minutes)\\b");

    private int parseDuration(String lower) {
        if (lower.contains("hour and a half") || lower.contains("hour and half")) {
            return 90;
        }
        if (lower.contains("half an hour") || lower.contains("half hour")) {
            return 30;
        }
        if (lower.contains("quarter of an hour") || lower.contains("quarter hour")) {
            return 15;
        }

        Matcher hoursMatcher = HOURS_MIN_PATTERN.matcher(lower);
        if (hoursMatcher.find()) {
            double hours = Double.parseDouble(hoursMatcher.group(1));
            return (int) Math.round(hours * 60);
        }

        Matcher minutesMatcher = MINUTES_PATTERN.matcher(lower);
        if (minutesMatcher.find()) {
            return Integer.parseInt(minutesMatcher.group(1));
        }

        if (containsWordish(lower, "hour") || lower.contains("an hour")) {
            return 60;
        }

        return DEFAULT_DURATION_MIN;
    }


    private record DayWindow(LocalDate from, LocalDate to) {
    }

    private static final List<String> WEEKDAY_NAMES = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private DayWindow parseDayWindow(String lower, LocalDate today) {
        if (lower.contains("today")) {
            return new DayWindow(today, today);
        }
        if (lower.contains("tomorrow")) {
            LocalDate tomorrow = today.plusDays(1);
            return new DayWindow(tomorrow, tomorrow);
        }
        if (lower.contains("this weekend") || lower.contains("weekend")) {
            LocalDate saturday = nextOrSame(today, DayOfWeek.SATURDAY);
            LocalDate sunday = saturday.plusDays(1);
            return new DayWindow(saturday, sunday);
        }
        if (lower.contains("next week")) {
            LocalDate nextMonday = nextOrSame(today, DayOfWeek.MONDAY);
            if (!nextMonday.isAfter(today)) {
                nextMonday = nextMonday.plusWeeks(1);
            }
            return new DayWindow(nextMonday, nextMonday.plusDays(6));
        }
        if (lower.contains("this week")) {
            LocalDate endOfWeek = nextOrSame(today, DayOfWeek.SUNDAY);
            return new DayWindow(today, endOfWeek);
        }

        List<LocalDate> weekdayMatches = new ArrayList<>();
        for (String name : WEEKDAY_NAMES) {
            if (containsWordish(lower, name)) {
                DayOfWeek dow = DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT));
                weekdayMatches.add(nextStrictly(today, dow));
            }
        }
        if (weekdayMatches.size() >= 2) {
            LocalDate min = weekdayMatches.stream().min(LocalDate::compareTo).orElse(today);
            LocalDate max = weekdayMatches.stream().max(LocalDate::compareTo).orElse(today);
            return new DayWindow(min, max);
        }
        if (weekdayMatches.size() == 1) {
            LocalDate d = weekdayMatches.get(0);
            return new DayWindow(d, d);
        }

        return new DayWindow(today, today.plusDays(DEFAULT_WINDOW_DAYS));
    }

    private LocalDate nextOrSame(LocalDate from, DayOfWeek target) {
        LocalDate d = from;
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        return d;
    }

    private LocalDate nextStrictly(LocalDate from, DayOfWeek target) {
        LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        return d;
    }


    private TimeOfDay parseTimeOfDay(String lower) {
        if (containsWordish(lower, "morning")) {
            return TimeOfDay.MORNING;
        }
        if (containsWordish(lower, "afternoon")) {
            return TimeOfDay.AFTERNOON;
        }
        if (containsWordish(lower, "evening") || containsWordish(lower, "tonight")
                || containsWordish(lower, "night")) {
            return TimeOfDay.EVENING;
        }
        return TimeOfDay.ANY;
    }


    private String parseResourceType(String lower) {
        for (ResourceType type : ResourceType.values()) {
            if (containsWordish(lower, type.name().toLowerCase(Locale.ROOT))) {
                return type.name();
            }
            if (containsWordish(lower, type.getDisplayName().toLowerCase(Locale.ROOT))) {
                return type.name();
            }
        }
        for (String[] syn : synonyms) {
            if (containsWordish(lower, syn[0])) {
                return syn[1];
            }
        }
        return null;
    }


    private static final Pattern FOR_N_PEOPLE = Pattern.compile("for\\s+(\\d+)\\s+(?:people|person|players|ppl)\\b");
    private static final java.util.Map<String, Integer> NUMBER_WORDS = java.util.Map.ofEntries(
            java.util.Map.entry("one", 1),
            java.util.Map.entry("two", 2),
            java.util.Map.entry("three", 3),
            java.util.Map.entry("four", 4),
            java.util.Map.entry("five", 5),
            java.util.Map.entry("six", 6),
            java.util.Map.entry("seven", 7),
            java.util.Map.entry("eight", 8),
            java.util.Map.entry("nine", 9),
            java.util.Map.entry("ten", 10)
    );

    private int parsePartySize(String lower, int fallback) {
        if (containsWordish(lower, "solo") || lower.contains("just me") || lower.contains("by myself")) {
            return 1;
        }
        if (lower.contains("with a friend") || lower.contains("with a buddy")) {
            return 2;
        }

        Matcher m = FOR_N_PEOPLE.matcher(lower);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }

        for (var entry : NUMBER_WORDS.entrySet()) {
            if (lower.contains("for " + entry.getKey())) {
                return entry.getValue();
            }
        }

        return Math.max(1, fallback);
    }
}
