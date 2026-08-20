package com.example.hackathoncodaro2026.voice.dto;

public class AvailabilitySlot {

    private String slotId;
    private String displayLabel;

    public AvailabilitySlot() {
    }

    public AvailabilitySlot(String slotId, String displayLabel) {
        this.slotId = slotId;
        this.displayLabel = displayLabel;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }
}
