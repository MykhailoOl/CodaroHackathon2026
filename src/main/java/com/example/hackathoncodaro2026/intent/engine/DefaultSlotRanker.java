package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RankResult;
import com.example.hackathoncodaro2026.intent.model.RelaxStep;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import com.example.hackathoncodaro2026.intent.model.ScoreTerm;
import com.example.hackathoncodaro2026.intent.model.Suggestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultSlotRanker implements SlotRanker {

    private static final int MIN_SURVIVORS = 2;

    @Override
    public RankResult rank(IntentSpec spec, ScheduleSnapshot snapshot, IntentProperties config) {
        Relaxer.State state = Relaxer.initial(spec);
        List<Suggestion> best = attempt(spec, snapshot, config, state);
        if (best.size() >= MIN_SURVIVORS) {
            return new RankResult(top(best, config.maxSuggestions()), List.of());
        }

        List<RelaxStep> trail = new ArrayList<>();
        for (Relaxer.Rung rung : Relaxer.ladder(spec, config)) {
            trail.add(rung.step());
            List<Suggestion> attempt = attempt(spec, snapshot, config, rung.state());
            if (attempt.size() > best.size()) {
                best = attempt;
            }
            if (best.size() >= MIN_SURVIVORS) {
                return new RankResult(top(best, config.maxSuggestions()), List.copyOf(trail));
            }
        }
        return new RankResult(top(best, config.maxSuggestions()), List.copyOf(trail));
    }

    private List<Suggestion> attempt(IntentSpec spec, ScheduleSnapshot snapshot, IntentProperties config,
                                      Relaxer.State state) {
        IntentSpec effectiveSpec = new IntentSpec(state.durationMin(), state.dayFrom(), state.dayTo(),
                spec.timeOfDay(), state.hardKeys(), state.softKeys(), spec.resourceType(), spec.partySize());

        List<ScoredCandidate> scored = new ArrayList<>();
        for (ResourceSlice resource : resourcesFor(spec, snapshot)) {
            List<Candidate> candidates = CandidateGenerator.generate(resource, effectiveSpec, snapshot, config,
                    state.dayFrom(), state.dayTo());
            List<Candidate> survivors = ConstraintFilter.filter(candidates, resource, snapshot, config,
                    state.hardKeys(), spec.partySize());
            for (Candidate c : survivors) {
                List<ScoreTerm> terms = Scorer.score(c, resource, effectiveSpec, snapshot, config,
                        state.softKeys(), state.dayFrom(), state.dayTo());
                double total = terms.stream().mapToDouble(ScoreTerm::delta).sum();
                scored.add(new ScoredCandidate(c, resource, terms, total));
            }
        }

        scored.sort(Comparator
                .comparingDouble(ScoredCandidate::total).reversed()
                .thenComparing(sc -> sc.candidate().start())
                .thenComparingLong(sc -> sc.resource().id()));

        List<Suggestion> suggestions = new ArrayList<>(scored.size());
        for (ScoredCandidate sc : scored) {
            String reason = Explainer.explain(sc.resource().name(), sc.resource().facilityName(), sc.terms());
            suggestions.add(new Suggestion(sc.resource().id(), sc.resource().name(), sc.resource().facilityName(),
                    sc.candidate().start(), sc.candidate().end(), sc.total(), sc.terms(), reason,
                    state.relaxedKeys()));
        }
        return suggestions;
    }

    private List<ResourceSlice> resourcesFor(IntentSpec spec, ScheduleSnapshot snapshot) {
        String type = spec.resourceType();
        if (type == null || type.isBlank()) {
            return snapshot.resources();
        }
        List<ResourceSlice> matched = snapshot.resources().stream()
                .filter(r -> type.equals(r.typeKey()))
                .toList();
        return matched.isEmpty() ? snapshot.resources() : matched;
    }

    private List<Suggestion> top(List<Suggestion> suggestions, int max) {
        return suggestions.stream().limit(Math.max(0, max)).toList();
    }

    private record ScoredCandidate(Candidate candidate, ResourceSlice resource, List<ScoreTerm> terms,
                                    double total) {
    }
}
