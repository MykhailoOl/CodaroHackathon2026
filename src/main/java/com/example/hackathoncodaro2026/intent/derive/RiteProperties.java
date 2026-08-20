package com.example.hackathoncodaro2026.intent.derive;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How long after a death each observance expects the service to take place, plus the
 * statutory outer limit that overrides custom.
 *
 * <p>Defaults are baked in so the feature works with no yml at all; a {@code rites:}
 * block in {@code application.yml} overrides them. This mirrors
 * {@link com.example.hackathoncodaro2026.config.DomainProperties} and deliberately
 * keys off the <em>rite</em> rather than {@code ResourceType}, so retargeting the
 * service catalogue and tuning the scheduling rules stay independent edits.
 *
 * @param rules               observance key -> rule
 * @param statutoryDays       legal outer limit measured from the date of death
 *                            (Poland: burial within 96 hours), applied to every
 *                            non-deferrable rite even when custom allows longer
 * @param certificateLeadDays typical wait for a death certificate after a natural death
 * @param coronerLeadDays     typical wait when a coroner or prosecutor must release the body
 * @param holdDecisionHour    hour of the day by which the family must answer for the
 *                            venue to hold the slot overnight
 */
@ConfigurationProperties(prefix = "rites")
public record RiteProperties(
        Map<String, RiteRule> rules,
        Integer statutoryDays,
        Integer certificateLeadDays,
        Integer coronerLeadDays,
        Integer holdDecisionHour
) {

    private static final Map<String, RiteRule> DEFAULT_RULES = defaultRules();

    public RiteProperties {
        rules = (rules == null || rules.isEmpty()) ? DEFAULT_RULES : normalizeKeys(rules);
        statutoryDays = statutoryDays == null || statutoryDays < 1 ? 4 : statutoryDays;
        certificateLeadDays = certificateLeadDays == null || certificateLeadDays < 0 ? 1 : certificateLeadDays;
        coronerLeadDays = coronerLeadDays == null || coronerLeadDays < 0 ? 4 : coronerLeadDays;
        holdDecisionHour = holdDecisionHour == null || holdDecisionHour < 0 || holdDecisionHour > 23
                ? 21 : holdDecisionHour;
    }

    public static RiteProperties defaults() {
        return new RiteProperties(null, null, null, null, null);
    }

    private static Map<String, RiteRule> normalizeKeys(Map<String, RiteRule> raw) {
        Map<String, RiteRule> normalized = new LinkedHashMap<>();
        raw.forEach((key, rule) -> {
            if (key != null && rule != null) {
                normalized.put(key.trim().toUpperCase(Locale.ROOT), rule);
            }
        });
        return Map.copyOf(normalized);
    }

    /** The rule for a rite key, or null when the rite is unknown or unstated. */
    public RiteRule rule(String riteKey) {
        if (riteKey == null || riteKey.isBlank()) {
            return null;
        }
        return rules.get(riteKey.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Every configured phrase paired with the rite it identifies, longest phrase first
     * so that "roman catholic" wins over a bare "catholic" and "greek orthodox" over
     * "orthodox".
     */
    public List<String[]> phrasePairs() {
        return rules.entrySet().stream()
                .flatMap(entry -> entry.getValue().phrases().stream()
                        .map(phrase -> new String[]{phrase.toLowerCase(Locale.ROOT), entry.getKey()}))
                .sorted((a, b) -> Integer.compare(b[0].length(), a[0].length()))
                .toList();
    }

    /**
     * @param label      one sentence naming the custom, shown to the family verbatim
     * @param latestDays days after the death by which the observance expects the service
     * @param deferrable true when the observance permits deferral — typically because
     *                   cremation or mortuary refrigeration is arranged — in which case
     *                   the statutory limit does not bind
     * @param phrases    words a family might use for this observance
     */
    public record RiteRule(
            String label,
            Integer latestDays,
            boolean deferrable,
            List<String> phrases
    ) {
        public RiteRule {
            latestDays = latestDays == null || latestDays < 0 ? 7 : latestDays;
            phrases = phrases == null ? List.of() : List.copyOf(phrases);
        }
    }

    private static Map<String, RiteRule> defaultRules() {
        Map<String, RiteRule> map = new LinkedHashMap<>();
        map.put("JEWISH", new RiteRule(
                "Jewish rite — burial before the next sunset", 1, false,
                List.of("jewish", "judaism", "hebrew", "chevra kadisha")));
        map.put("MUSLIM", new RiteRule(
                "Islamic rite — burial as soon as possible, within a day", 1, false,
                List.of("muslim", "islamic", "islam", "janazah")));
        map.put("ORTHODOX", new RiteRule(
                "Orthodox rite — within three days", 3, false,
                List.of("greek orthodox", "russian orthodox", "orthodox")));
        map.put("CATHOLIC", new RiteRule(
                "Catholic rite — customarily on the third to fifth day", 5, false,
                List.of("roman catholic", "catholic", "requiem", "requiem mass")));
        map.put("PROTESTANT", new RiteRule(
                "Protestant service — usually within a week", 7, false,
                List.of("protestant", "lutheran", "evangelical", "anglican", "baptist")));
        map.put("HUMANIST", new RiteRule(
                "Humanist ceremony — no religious deadline", 10, true,
                List.of("humanist", "secular", "non-religious", "no religious", "civil ceremony", "no service")));
        return Map.copyOf(map);
    }
}
