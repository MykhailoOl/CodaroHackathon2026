package com.example.hackathoncodaro2026.voice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckAvailabilityRequest {

    @JsonAlias({"system__agent_id", "agent_id"})
    private String agentId;

    private String text;

    private String sport;

    private String preferredDay;

    @JsonAlias({"preferredPartOfDay"})
    private String partOfDay;

    private String preferredPartOfDay;

    private String preferredTime;

    private String language = "en";

    private Integer durationHours;

    private Integer partySize;

    @JsonAlias({"skip_count"})
    private Integer skipCount;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Object partySize) {
        this.partySize = SpokenIntegers.parse(partySize);
    }

    public Integer getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(Object skipCount) {
        if (skipCount == null) {
            this.skipCount = 0;
            return;
        }
        if (skipCount instanceof Number number) {
            this.skipCount = Math.max(0, number.intValue());
            return;
        }
        Integer parsed = SpokenIntegers.parse(skipCount);
        this.skipCount = parsed == null ? 0 : parsed;
    }

    public int skipCount() {
        return skipCount == null || skipCount < 0 ? 0 : skipCount;
    }

    public String resolvedText() {
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        StringBuilder composed = new StringBuilder();
        append(composed, sport);
        append(composed, preferredDay);
        append(composed, resolvedPartOfDay());
        append(composed, preferredTime);
        return composed.toString().trim();
    }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value.trim());
    }
}
