package com.example.hackathoncodaro2026.voice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateBookingRequest {

    @JsonAlias({"system__agent_id", "agent_id"})
    private String agentId;

    @JsonAlias({"system__conversation_id", "conversation_id"})
    private String conversationId;

    private String slotId;

    private Long resourceId;

    private LocalDateTime start;

    private LocalDateTime end;

    private Integer partySize;

    @JsonAlias({"patientName", "callerName", "deceasedName"})
    private String playerName;

    @JsonAlias({"deceasedFullName"})
    private String deceasedFullName;

    private LocalDate dateOfDeath;

    @JsonAlias({"patientPhone", "system__caller_id", "caller_id"})
    private String playerPhone;

    private String language = "en";

    private String notes;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    @JsonAlias({"venueId", "resource_id"})
    public void setResourceId(Object resourceId) {
        this.resourceId = SpokenIntegers.parseLong(resourceId);
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Object partySize) {
        this.partySize = SpokenIntegers.parse(partySize);
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getDeceasedFullName() {
        return deceasedFullName;
    }

    public void setDeceasedFullName(String deceasedFullName) {
        this.deceasedFullName = deceasedFullName;
    }

    public LocalDate getDateOfDeath() {
        return dateOfDeath;
    }

    public void setDateOfDeath(LocalDate dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
    }

    public String getPlayerPhone() {
        return playerPhone;
    }

    public void setPlayerPhone(String playerPhone) {
        this.playerPhone = sanitizePhone(playerPhone);
    }

    private static String sanitizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("system__") || lower.contains("{{") || lower.equals("caller_id")) {
            return null;
        }
        return trimmed;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
