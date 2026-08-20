package com.example.hackathoncodaro2026.voice.dto;

import java.time.LocalDateTime;

public class AvailabilitySlot {

    private String slotId;
    private String displayLabel;
    private Long resourceId;
    private LocalDateTime start;
    private LocalDateTime end;
    private String price;
    private Integer partySize;

    public AvailabilitySlot() {
    }

    public AvailabilitySlot(
            String slotId,
            String displayLabel,
            Long resourceId,
            LocalDateTime start,
            LocalDateTime end,
            String price,
            Integer partySize
    ) {
        this.slotId = slotId;
        this.displayLabel = displayLabel;
        this.resourceId = resourceId;
        this.start = start;
        this.end = end;
        this.price = price;
        this.partySize = partySize;
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

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
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

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Integer partySize) {
        this.partySize = partySize;
    }
}
