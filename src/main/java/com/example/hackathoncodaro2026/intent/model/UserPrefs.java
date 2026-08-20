package com.example.hackathoncodaro2026.intent.model;

public record UserPrefs(int preferredFromMin, int preferredToMin, int minBufferMin) {

    public static UserPrefs none() {
        return new UserPrefs(0, 0, 0);
    }

    public boolean hasPreferredWindow() {
        return preferredToMin > preferredFromMin;
    }
}
