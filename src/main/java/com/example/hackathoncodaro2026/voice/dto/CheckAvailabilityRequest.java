package com.example.hackathoncodaro2026.voice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckAvailabilityRequest {

    @JsonAlias({"system__agent_id", "agent_id"})
    private String agentId;

    private String sport;

    private String preferredDay;

    @JsonAlias({"preferredPartOfDay"})
    private String partOfDay;

    private String preferredPartOfDay;

    private String preferredTime;

    private String language = "en";

    private Integer durationHours;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSport() {
        return sport;
    }

    public void setSport(String sport) {
        this.sport = sport;
    }

    public String getPreferredDay() {
        return preferredDay;
    }

    public void setPreferredDay(String preferredDay) {
        this.preferredDay = preferredDay;
    }

    public String getPartOfDay() {
        return partOfDay;
    }

    public void setPartOfDay(String partOfDay) {
        this.partOfDay = partOfDay;
    }

    public String getPreferredPartOfDay() {
        return preferredPartOfDay;
    }

    public void setPreferredPartOfDay(String preferredPartOfDay) {
        this.preferredPartOfDay = preferredPartOfDay;
    }

    public String resolvedPartOfDay() {
        if (preferredPartOfDay != null && !preferredPartOfDay.isBlank()) {
            return preferredPartOfDay;
        }
        return partOfDay;
    }

    public String getPreferredTime() {
        return preferredTime;
    }

    public void setPreferredTime(String preferredTime) {
        this.preferredTime = preferredTime;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(Integer durationHours) {
        this.durationHours = durationHours;
    }
}
