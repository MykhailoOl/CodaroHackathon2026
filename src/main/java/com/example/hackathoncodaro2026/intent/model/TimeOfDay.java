package com.example.hackathoncodaro2026.intent.model;

public enum TimeOfDay {
    ANY(0, 24 * 60),
    MORNING(6 * 60, 12 * 60),
    AFTERNOON(12 * 60, 17 * 60),
    EVENING(17 * 60, 22 * 60);

    private final int fromMin;
    private final int toMin;

    TimeOfDay(int fromMin, int toMin) {
        this.fromMin = fromMin;
        this.toMin = toMin;
    }

    public int fromMin() {
        return fromMin;
    }

    public int toMin() {
        return toMin;
    }
}
