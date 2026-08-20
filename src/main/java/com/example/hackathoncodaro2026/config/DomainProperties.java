package com.example.hackathoncodaro2026.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "domain")
public record DomainProperties(
        Brand brand,
        String llmDomainDescription,
        Map<String, List<String>> resourceSynonyms
) {

    private static final Map<String, List<String>> DEFAULT_SYNONYMS = Map.of(
            "CHAPEL", List.of("chapel", "ceremony", "service", "funeral"),
            "CREMATION", List.of("cremation", "crematorium"),
            "BURIAL", List.of("burial", "grave", "plot", "interment"),
            "TRANSPORT", List.of("hearse", "cortege"),
            "RECEPTION", List.of("wake", "reception"),
            "VIEWING", List.of("viewing", "repose", "visitation"),
            "REPATRIATION", List.of("repatriation")
    );

    public DomainProperties {
        brand = brand == null ? Brand.defaults() : brand;
        llmDomainDescription = llmDomainDescription == null || llmDomainDescription.isBlank()
                ? "funeral-services"
                : llmDomainDescription.trim();
        resourceSynonyms = (resourceSynonyms == null || resourceSynonyms.isEmpty())
                ? DEFAULT_SYNONYMS
                : Map.copyOf(resourceSynonyms);
    }

    public static DomainProperties defaults() {
        return new DomainProperties(Brand.defaults(), "funeral-services", Map.of());
    }

    public List<String[]> synonymPairs() {
        return resourceSynonyms.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().stream()
                        .map(synonym -> new String[]{synonym, entry.getKey()}))
                .toList();
    }

    public record Brand(String name, String tagline) {

        public Brand {
            name = name == null || name.isBlank() ? "EverRest" : name.trim();
            tagline = tagline == null || tagline.isBlank() ? "Arrangements, within the time you have" : tagline.trim();
        }

        public static Brand defaults() {
            return new Brand("EverRest", "Arrangements, within the time you have");
        }
    }
}