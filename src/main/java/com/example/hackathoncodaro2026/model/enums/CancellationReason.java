package com.example.hackathoncodaro2026.model.enums;

public enum CancellationReason {
    CHANGE_OF_PLANS("Change of plans"),
    BOOKED_BY_MISTAKE("Booked by mistake"),
    SCHEDULING_CONFLICT("Scheduling conflict"),
    OTHER("Other");

    private final String label;

    CancellationReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
