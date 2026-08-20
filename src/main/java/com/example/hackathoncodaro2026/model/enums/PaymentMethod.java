package com.example.hackathoncodaro2026.model.enums;

public enum PaymentMethod {
    CASH("Cash"),
    CARD_ON_SITE("Card at the facility"),
    ONLINE_TRANSFER("Bank transfer"),
    INVOICE("Invoice to the family");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
