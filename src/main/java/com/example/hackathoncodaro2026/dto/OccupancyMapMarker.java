package com.example.hackathoncodaro2026.dto;

public class OccupancyMapMarker {

    private String kind;
    private String name;
    private String district;
    private String detail;
    private String status;
    private String channel;
    private double x;
    private double y;

    public boolean isPerson() {
        return "person".equals(kind);
    }

    public String getGivenName() {
        if (name == null || name.isBlank()) {
            return "Someone";
        }
        return name.trim().split("\\s+")[0];
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }
}
