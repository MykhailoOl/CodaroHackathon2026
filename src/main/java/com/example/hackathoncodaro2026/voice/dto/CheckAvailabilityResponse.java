package com.example.hackathoncodaro2026.voice.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CheckAvailabilityResponse {

    private String requestId = UUID.randomUUID().toString();
    private List<AvailabilitySlot> slots = new ArrayList<>();
    private boolean widened;

    public CheckAvailabilityResponse() {
    }

    public CheckAvailabilityResponse(List<AvailabilitySlot> slots, boolean widened) {
        this.slots = slots;
        this.widened = widened;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<AvailabilitySlot> getSlots() {
        return slots;
    }

    public void setSlots(List<AvailabilitySlot> slots) {
        this.slots = slots;
    }

    public boolean isWidened() {
        return widened;
    }

    public void setWidened(boolean widened) {
        this.widened = widened;
    }
}
