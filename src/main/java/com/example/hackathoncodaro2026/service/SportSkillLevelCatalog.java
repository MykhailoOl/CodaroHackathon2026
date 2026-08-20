package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.model.SportSkillLevel;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SportSkillLevelCatalog {

    private final Map<ResourceType, List<SportSkillLevel>> levelsBySport = new EnumMap<>(ResourceType.class);

    public SportSkillLevelCatalog() {
        levelsBySport.put(ResourceType.CHAPEL, denominations());
        levelsBySport.put(ResourceType.BURIAL, denominations());
        levelsBySport.put(ResourceType.CREMATION, cremationRites());
        levelsBySport.put(ResourceType.VIEWING, cremationRites());
        levelsBySport.put(ResourceType.RECEPTION, hostingRoles());
        levelsBySport.put(ResourceType.TRANSPORT, bearerRoles());
        levelsBySport.put(ResourceType.REPATRIATION, bearerRoles());
    }

    public List<SportSkillLevel> levelsFor(ResourceType sport) {
        if (sport == null) {
            return List.of();
        }
        return levelsBySport.getOrDefault(sport, List.of());
    }

    public Map<String, List<SportSkillLevel>> optionsBySport() {
        Map<String, List<SportSkillLevel>> options = new LinkedHashMap<>();
        for (ResourceType sport : ResourceType.values()) {
            options.put(sport.name(), levelsFor(sport));
        }
        return options;
    }

    public boolean isValid(ResourceType sport, String code) {
        if (sport == null || code == null || code.isBlank()) {
            return false;
        }
        String needle = code.trim();
        return levelsFor(sport).stream().anyMatch(level -> level.getCode().equals(needle));
    }

    public String label(ResourceType sport, String code) {
        if (sport == null || code == null || code.isBlank()) {
            return "";
        }
        String needle = code.trim();
        return levelsFor(sport).stream()
                .filter(level -> level.getCode().equals(needle))
                .map(SportSkillLevel::getLabel)
                .findFirst()
                .orElse(needle);
    }

    public String joinedLabels(ResourceType sport, Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> label(sport, code))
                .collect(Collectors.joining(", "));
    }

    public Set<String> codesFor(ResourceType sport) {
        return levelsFor(sport).stream().map(SportSkillLevel::getCode).collect(Collectors.toSet());
    }

    public String groupLabel(ResourceType sport) {
        if (sport == null) {
            return "Level";
        }
        return switch (sport) {
            case CHAPEL, BURIAL -> "Rite / denomination";
            case CREMATION, VIEWING -> "Ceremony type";
            case RECEPTION -> "Hosting role";
            case TRANSPORT, REPATRIATION -> "Bearer role";
        };
    }

    /**
     * What a celebrant is qualified to lead. These are the "coach levels" of the old
     * product: an officiant attaches to a service the way a coach attached to a court,
     * and a family filters on the rite rather than on a playing standard.
     */
    private List<SportSkillLevel> denominations() {
        return List.of(
                new SportSkillLevel("CATHOLIC", "Roman Catholic priest"),
                new SportSkillLevel("ORTHODOX", "Orthodox priest"),
                new SportSkillLevel("PROTESTANT", "Protestant minister"),
                new SportSkillLevel("JEWISH", "Rabbi"),
                new SportSkillLevel("MUSLIM", "Imam"),
                new SportSkillLevel("HUMANIST", "Humanist celebrant"),
                new SportSkillLevel("CIVIL", "Civil officiant")
        );
    }

    private List<SportSkillLevel> cremationRites() {
        return List.of(
                new SportSkillLevel("COMMITTAL", "Committal service"),
                new SportSkillLevel("DIRECT", "Direct, no ceremony"),
                new SportSkillLevel("MEMORIAL", "Memorial with ashes"),
                new SportSkillLevel("HUMANIST", "Humanist celebrant")
        );
    }

    private List<SportSkillLevel> hostingRoles() {
        return List.of(
                new SportSkillLevel("HOST", "Reception host"),
                new SportSkillLevel("CATERING", "Catering manager"),
                new SportSkillLevel("MUSIC", "Musician")
        );
    }

    private List<SportSkillLevel> bearerRoles() {
        return List.of(
                new SportSkillLevel("DRIVER", "Hearse driver"),
                new SportSkillLevel("BEARER", "Bearer"),
                new SportSkillLevel("CONDUCTOR", "Funeral conductor")
        );
    }
}
