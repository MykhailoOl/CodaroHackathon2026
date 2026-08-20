package com.example.hackathoncodaro2026.intent.model;

import java.util.List;

public record RelaxStep(Action action, String detail, List<String> droppedKeys) {

    public enum Action {
        WIDEN_DAY_WINDOW,
        DROP_SOFT_CONSTRAINT,
        DROP_HARD_CONSTRAINT,
        SHRINK_DURATION
    }

    public RelaxStep {
        droppedKeys = droppedKeys == null ? List.of() : List.copyOf(droppedKeys);
    }
}
