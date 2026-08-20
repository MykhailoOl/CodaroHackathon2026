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
            "FOOTBALL", List.of("football", "soccer"),
            "GYM", List.of("gym", "workout"),
            "SWIMMING", List.of("pool", "swimming", "swim")
    );

    public DomainProperties {
        brand = brand == null ? Brand.defaults() : brand;
        llmDomainDescription = llmDomainDescription == null || llmDomainDescription.isBlank()
                ? "sports-facility"
                : llmDomainDescription.trim();
        resourceSynonyms = (resourceSynonyms == null || resourceSynonyms.isEmpty())
                ? DEFAULT_SYNONYMS
                : Map.copyOf(resourceSynonyms);
    }

    public static DomainProperties defaults() {
        return new DomainProperties(Brand.defaults(), "sports-facility", Map.of());
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
            name = name == null || name.isBlank() ? "Courtly" : name.trim();
            tagline = tagline == null || tagline.isBlank() ? "Sports Facility Booking" : tagline.trim();
        }

        public static Brand defaults() {
            return new Brand("Courtly", "Sports Facility Booking");
        }
    }
}