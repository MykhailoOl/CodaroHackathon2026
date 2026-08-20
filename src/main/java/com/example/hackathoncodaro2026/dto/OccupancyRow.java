package com.example.hackathoncodaro2026.dto;

import java.util.ArrayList;
import java.util.List;

public class OccupancyRow {

    private Long venueId;
    private String venueName;
    private String funeralHomeName;
    private String venueType;
    private String imagePath;
    private int maxAttendees;
    private List<OccupancyCell> cells = new ArrayList<>();

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getFuneralHomeName() {
        return funeralHomeName;
    }

    public void setFuneralHomeName(String funeralHomeName) {
        this.funeralHomeName = funeralHomeName;
    }

    public String getVenueType() {
        return venueType;
    }

    public void setVenueType(String venueType) {
        this.venueType = venueType;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getMaxAttendees() {
        return maxAttendees;
    }

    public void setMaxAttendees(int maxAttendees) {
        this.maxAttendees = maxAttendees;
    }

    public List<OccupancyCell> getCells() {
        return cells;
    }

    public void setCells(List<OccupancyCell> cells) {
        this.cells = cells;
    }
}
