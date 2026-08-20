package com.example.hackathoncodaro2026.model.enums;

public enum Role {
    USER("Family"),
    MANAGER("Manager"),
    ADMIN("Admin");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
