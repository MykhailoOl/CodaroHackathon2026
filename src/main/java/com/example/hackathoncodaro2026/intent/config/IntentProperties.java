package com.example.hackathoncodaro2026.intent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "intent")
public record IntentProperties(
        int granularityMin,
        int bufferMin,
        int maxSuggestions,
        int maxCandidatesPerInterval,
        double shrinkDurationMaxPct,
        Weights weights,
        List<ConstraintRule> constraints,
        Map<String, Map<String, String>> resourceAttributes
) {

    public IntentProperties {
        granularityMin = granularityMin <= 0 ? 15 : granularityMin;
        bufferMin = bufferMin < 0 ? 0 : bufferMin;
        maxSuggestions = maxSuggestions <= 0 ? 3 : maxSuggestions;
        maxCandidatesPerInterval = maxCandidatesPerInterval <= 0 ? 12 : maxCandidatesPerInterval;
        shrinkDurationMaxPct = shrinkDurationMaxPct <= 0 ? 0.25 : shrinkDurationMaxPct;
        weights = weights == null ? Weights.defaults() : weights;
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        resourceAttributes = resourceAttributes == null ? Map.of() : Map.copyOf(resourceAttributes);
    }

    public Map<String, String> attributesFor(String typeKey) {
        return resourceAttributes.getOrDefault(typeKey, Map.of());
    }

    public record Weights(
            double timeOfDay,
            double dayProximity,
            double buffer,
            double fragmentation,
            double resourceLoad,
            double workload
    ) {
        public static Weights defaults() {
            return new Weights(20, 15, 15, 20, 15, 15);
        }
    }

    public enum Kind { HARD, SOFT }

    public record ConstraintRule(
            String key,
            Kind kind,
            boolean relaxable,
            String label,
            double weight,
            String attribute,
            String equalsValue,
            List<String> phrases
    ) {
        public ConstraintRule {
            phrases = phrases == null ? List.of() : List.copyOf(phrases);
            kind = kind == null ? Kind.SOFT : kind;
        }

        public boolean isHard() {
            return kind == Kind.HARD;
        }
    }

    public ConstraintRule constraint(String key) {
        return constraints.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
    }

    public List<ConstraintRule> hardConstraints() {
        return constraints.stream().filter(ConstraintRule::isHard).toList();
    }

    public List<ConstraintRule> softConstraints() {
        return constraints.stream().filter(c -> !c.isHard()).toList();
    }
}
