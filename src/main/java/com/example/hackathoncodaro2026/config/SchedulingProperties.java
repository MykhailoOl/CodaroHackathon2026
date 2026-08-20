package com.example.hackathoncodaro2026.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scheduling")
public class SchedulingProperties {

    private int planningDays = 21;
    private boolean sundayEnabled = false;
    private int candidateDates = 10;

    public int getPlanningDays() {
        return planningDays;
    }

    public void setPlanningDays(int planningDays) {
        this.planningDays = planningDays;
    }

    public boolean isSundayEnabled() {
        return sundayEnabled;
    }

    public void setSundayEnabled(boolean sundayEnabled) {
        this.sundayEnabled = sundayEnabled;
    }

    public int getCandidateDates() {
        return candidateDates;
    }

    public void setCandidateDates(int candidateDates) {
        this.candidateDates = candidateDates;
    }
}
