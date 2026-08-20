package com.example.hackathoncodaro2026.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class OccupancyGrid {

    private LocalDate date;
    private Long funeralHomeId;
    private List<LocalTime> hours = new ArrayList<>();
    private List<OccupancyRow> rows = new ArrayList<>();
    private int bookedUnits;
    private int capacityUnits;

    public int getFillPercent() {
        if (capacityUnits <= 0) {
            return 0;
        }
        return (int) Math.round(bookedUnits * 100.0 / capacityUnits);
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getFuneralHomeId() {
        return funeralHomeId;
    }

    public void setFuneralHomeId(Long funeralHomeId) {
        this.funeralHomeId = funeralHomeId;
    }

    public List<LocalTime> getHours() {
        return hours;
    }

    public void setHours(List<LocalTime> hours) {
        this.hours = hours;
    }

    public List<OccupancyRow> getRows() {
        return rows;
    }

    public void setRows(List<OccupancyRow> rows) {
        this.rows = rows;
    }

    public int getBookedUnits() {
        return bookedUnits;
    }

    public void setBookedUnits(int bookedUnits) {
        this.bookedUnits = bookedUnits;
    }

    public int getCapacityUnits() {
        return capacityUnits;
    }

    public void setCapacityUnits(int capacityUnits) {
        this.capacityUnits = capacityUnits;
    }
}
