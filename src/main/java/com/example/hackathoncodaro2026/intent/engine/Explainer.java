package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.model.ScoreTerm;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class Explainer {

    private Explainer() {
    }

    static String explain(String resourceName, String facilityName, List<ScoreTerm> terms) {
        List<ScoreTerm> top = terms.stream()
                .sorted(Comparator.comparingDouble((ScoreTerm t) -> Math.abs(t.delta())).reversed())
                .limit(3)
                .toList();

        String place = resourceName + " (" + facilityName + ")";
        if (top.isEmpty()) {
            return "Recommended at " + place + ".";
        }
        String reasons = top.stream()
                .map(t -> t.label() + (t.delta() >= 0 ? " in its favor" : " as a trade-off"))
                .collect(Collectors.joining(", "));
        return "Recommended at " + place + " mainly because of " + reasons + ".";
    }
}
