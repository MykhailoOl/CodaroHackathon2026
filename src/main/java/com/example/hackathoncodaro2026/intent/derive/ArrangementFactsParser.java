package com.example.hackathoncodaro2026.intent.derive;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArrangementFactsParser {

    private static final int DEATH_CLAUSE_CHARS = 80;

    private static final int FAMILY_ONLY_MOURNERS = 8;

    private static final Pattern DEATH_PHRASE = Pattern.compile(
            "\\b(?:passed away|passed on|passed|died|death|deceased|we lost (?:her|him|them|my|our))\\b");

    private static final Pattern DAYS_AGO = Pattern.compile("\\b(\\d{1,2})\\s+days?\\s+ago\\b");
    private static final Pattern WEEKS_AGO = Pattern.compile("\\b(\\d{1,2})\\s+weeks?\\s+ago\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b");
    private static final Pattern NUMERIC_DATE = Pattern.compile("\\b(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2,4}))?\\b");

    private static final Pattern MOURNERS = Pattern.compile(
            "\\b(?:about|around|roughly|approximately|some|expecting|expect)?\\s*(\\d{1,4})\\s*"
                    + "(?:mourners|guests|attendees|people|persons|family members)\\b");
    private static final Pattern MOURNERS_TRAILING = Pattern.compile(
            "\\b(?:mourners|guests|attendees|attendance)\\D{0,12}?(\\d{1,4})\\b");

    private static final Pattern CERTIFICATE_ON = Pattern.compile(
            "\\bcertificate\\b[^.,;]{0,40}?\\b(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b");
    private static final Pattern CERTIFICATE_READY = Pattern.compile(
            "\\b(?:certificate (?:is )?(?:ready|released|issued|in hand|signed)"
                    + "|(?:we|i) (?:have|hold|['a-z]*ve got) the (?:death )?certificate)\\b");
    private static final Pattern CORONER = Pattern.compile(
            "\\b(?:coroner|post[- ]?mortem|postmortem|autopsy|inquest|prosecutor|forensic)\\b");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private final RiteProperties rites;

    public ArrangementFactsParser(RiteProperties rites) {
        this.rites = rites;
    }

    public ArrangementFacts parse(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return ArrangementFacts.none();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        LocalDate dateOfDeath = parseDateOfDeath(lower, today);
        return new ArrangementFacts(
                dateOfDeath,
                parseRite(lower),
                parseCertificateDate(lower, today, dateOfDeath),
                parseMourners(lower)
        );
    }

    private LocalDate parseDateOfDeath(String lower, LocalDate today) {
        Matcher phrase = DEATH_PHRASE.matcher(lower);
        if (!phrase.find()) {
            return null;
        }
        int start = phrase.start();
        String clause = lower.substring(start, Math.min(lower.length(), phrase.end() + DEATH_CLAUSE_CHARS));

        LocalDate fromClause = pastDateIn(clause, today, true);
        if (fromClause != null) {
            return fromClause;
        }
        return pastDateIn(lower, today, false);
    }

    private LocalDate pastDateIn(String segment, LocalDate today, boolean allowWeekdays) {
        if (segment.contains("this morning") || segment.contains("this afternoon")
                || segment.contains("earlier today") || segment.contains("today")) {
            return today;
        }
        if (segment.contains("last night") || segment.contains("overnight")
                || segment.contains("yesterday")) {
            return today.minusDays(1);
        }
        Matcher days = DAYS_AGO.matcher(segment);
        if (days.find()) {
            return today.minusDays(Integer.parseInt(days.group(1)));
        }
        Matcher weeks = WEEKS_AGO.matcher(segment);
        if (weeks.find()) {
            return today.minusWeeks(Integer.parseInt(weeks.group(1)));
        }

        LocalDate explicit = explicitDate(segment, today);
        if (explicit != null) {
            return explicit;
        }
        if (!allowWeekdays) {
            return null;
        }
        for (String name : WEEKDAYS) {
            if (Pattern.compile("\\b" + name + "\\b").matcher(segment).find()) {
                return mostRecent(today, DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT)));
            }
        }
        return null;
    }

    private LocalDate explicitDate(String segment, LocalDate today) {
        Matcher dm = DAY_MONTH.matcher(segment);
        if (dm.find()) {
            return notInFuture(MONTHS.get(dm.group(2)), Integer.parseInt(dm.group(1)), today);
        }
        Matcher md = MONTH_DAY.matcher(segment);
        if (md.find()) {
            return notInFuture(MONTHS.get(md.group(1)), Integer.parseInt(md.group(2)), today);
        }
        Matcher nd = NUMERIC_DATE.matcher(segment);
        if (nd.find()) {
            int day = Integer.parseInt(nd.group(1));
            int month = Integer.parseInt(nd.group(2));
            Integer year = nd.group(3) == null ? null : Integer.parseInt(nd.group(3));
            if (year != null && year < 100) {
                year += 2000;
            }
            LocalDate candidate = safeDate(year == null ? today.getYear() : year, month, day);
            if (candidate == null) {
                return null;
            }
            return year != null ? candidate : notInFuture(month, day, today);
        }
        return null;
    }

    private LocalDate notInFuture(Integer month, int day, LocalDate today) {
        if (month == null) {
            return null;
        }
        LocalDate candidate = safeDate(today.getYear(), month, day);
        if (candidate == null) {
            return null;
        }
        return candidate.isAfter(today) ? candidate.minusYears(1) : candidate;
    }

    private LocalDate safeDate(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate mostRecent(LocalDate today, DayOfWeek target) {
        LocalDate d = today;
        while (d.getDayOfWeek() != target) {
            d = d.minusDays(1);
        }
        return d;
    }

    private String parseRite(String lower) {
        for (String[] pair : rites.phrasePairs()) {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(pair[0]) + "(?![a-z])").matcher(lower).find()) {
                return pair[1];
            }
        }
        return null;
    }

    private LocalDate parseCertificateDate(String lower, LocalDate today, LocalDate dateOfDeath) {
        if (CERTIFICATE_READY.matcher(lower).find()) {
            return today;
        }
        Matcher on = CERTIFICATE_ON.matcher(lower);
        if (on.find()) {
            return futureDay(on.group(1), today);
        }
        if (CORONER.matcher(lower).find() && dateOfDeath != null) {
            return dateOfDeath.plusDays(rites.coronerLeadDays());
        }
        return null;
    }

    private LocalDate futureDay(String word, LocalDate today) {
        if ("today".equals(word)) {
            return today;
        }
        if ("tomorrow".equals(word)) {
            return today.plusDays(1);
        }
        DayOfWeek target = DayOfWeek.valueOf(word.toUpperCase(Locale.ROOT));
        LocalDate d = today;
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        return d;
    }

    private Integer parseMourners(String lower) {
        if (lower.contains("family only") || lower.contains("just family")
                || lower.contains("close family") || lower.contains("immediate family")) {
            return FAMILY_ONLY_MOURNERS;
        }
        Matcher m = MOURNERS.matcher(lower);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        Matcher trailing = MOURNERS_TRAILING.matcher(lower);
        if (trailing.find()) {
            return Integer.parseInt(trailing.group(1));
        }
        return null;
    }
}
