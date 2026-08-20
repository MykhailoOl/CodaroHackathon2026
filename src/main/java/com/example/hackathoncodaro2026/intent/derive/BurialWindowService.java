package com.example.hackathoncodaro2026.intent.derive;

import com.example.hackathoncodaro2026.intent.derive.RiteProperties.RiteRule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BurialWindowService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final int PREFERENCE_MAX_SPAN_DAYS = 2;
    private static final int INFEASIBLE_GRACE_DAYS = 3;

    private final RiteProperties rites;

    public BurialWindowService(RiteProperties rites) {
        this.rites = rites;
    }

    public Optional<ServiceWindow> derive(ArrangementFacts facts, LocalDateTime now) {
        if (facts == null || !facts.schedulable()) {
            return Optional.empty();
        }
        LocalDate today = now.toLocalDate();
        LocalDate death = facts.dateOfDeath();
        List<String> why = new ArrayList<>();
        why.add("Death recorded " + DAY.format(death) + ".");

        LocalDate earliest = deriveEarliest(facts, death, today, why);
        RiteRule rule = rites.rule(facts.rite());
        LocalDate latest = deriveLatest(rule, death, why);

        boolean feasible = !earliest.isAfter(latest);
        if (!feasible) {
            why.add("The certificate cannot arrive before that deadline. "
                    + "The service is placed as early as the release allows; the funeral director "
                    + "must file for an extension of the statutory period.");
            latest = earliest.plusDays(INFEASIBLE_GRACE_DAYS);
        }

        return Optional.of(new ServiceWindow(
                earliest,
                latest,
                rule == null ? null : normalizedRite(facts.rite()),
                why,
                decisionDeadline(now, latest),
                feasible
        ));
    }

    private LocalDate deriveEarliest(ArrangementFacts facts, LocalDate death, LocalDate today, List<String> why) {
        LocalDate certificate;
        if (facts.certificateReadyOn() != null) {
            certificate = facts.certificateReadyOn();
            why.add("Death certificate expected " + DAY.format(certificate)
                    + " — nothing can be scheduled before it is released.");
        } else {
            certificate = death.plusDays(rites.certificateLeadDays());
            why.add("No certificate date given; assuming the usual "
                    + dayCount(rites.certificateLeadDays()) + " wait, so " + DAY.format(certificate) + ".");
        }
        if (certificate.isBefore(today)) {
            why.add("That date has passed, so the earliest possible service is today, "
                    + DAY.format(today) + ".");
            return today;
        }
        return certificate;
    }

    private LocalDate deriveLatest(RiteRule rule, LocalDate death, List<String> why) {
        LocalDate statutory = death.plusDays(rites.statutoryDays());
        if (rule == null) {
            why.add("No observance stated; applying the statutory limit of "
                    + rites.statutoryDays() * 24 + " hours from death, so on or before "
                    + DAY.format(statutory) + ".");
            return statutory;
        }
        LocalDate latest = death.plusDays(rule.latestDays());
        why.add(rule.label() + ". That puts the service on or before " + DAY.format(latest) + ".");
        if (!rule.deferrable() && statutory.isBefore(latest)) {
            why.add("Statutory limit: burial within " + rites.statutoryDays() * 24
                    + " hours of death — this shortens the customary window to "
                    + DAY.format(statutory) + ".");
            return statutory;
        }
        return latest;
    }

    private LocalDateTime decisionDeadline(LocalDateTime now, LocalDate latest) {
        LocalDateTime candidate = latest.minusDays(1).atTime(rites.holdDecisionHour(), 0);
        LocalDateTime floor = now.plusHours(2);
        return candidate.isBefore(floor) ? floor : candidate;
    }

    private String normalizedRite(String rite) {
        return rite == null ? null : rite.trim().toUpperCase(Locale.ROOT);
    }

    private String dayCount(int days) {
        return days == 1 ? "one-day" : days + "-day";
    }

    public EffectiveRange applyPreference(
            ServiceWindow window,
            LocalDate dateOfDeath,
            LocalDate preferredFrom,
            LocalDate preferredTo
    ) {
        if (window == null) {
            return new EffectiveRange(preferredFrom, preferredTo, null);
        }
        if (preferredFrom == null || preferredTo == null || preferredFrom.isAfter(preferredTo)
                || preferredFrom.plusDays(PREFERENCE_MAX_SPAN_DAYS).isBefore(preferredTo)) {
            return new EffectiveRange(window.earliest(), window.latest(), null);
        }
        if (dateOfDeath != null && !preferredTo.isAfter(dateOfDeath)) {
            return new EffectiveRange(window.earliest(), window.latest(), null);
        }

        LocalDate from = preferredFrom.isAfter(window.earliest()) ? preferredFrom : window.earliest();
        LocalDate to = preferredTo.isBefore(window.latest()) ? preferredTo : window.latest();
        if (from.isAfter(to)) {
            return new EffectiveRange(
                    window.earliest(),
                    window.latest(),
                    "You asked for " + describe(preferredFrom, preferredTo)
                            + ", which falls outside the window. The nearest possible dates are offered instead."
            );
        }
        return new EffectiveRange(from, to, "You asked for " + describe(from, to) + ", which the window allows.");
    }

    private String describe(LocalDate from, LocalDate to) {
        return from.equals(to) ? DAY.format(from) : DAY.format(from) + " – " + DAY.format(to);
    }

    public record EffectiveRange(LocalDate from, LocalDate to, String note) {
    }
}
