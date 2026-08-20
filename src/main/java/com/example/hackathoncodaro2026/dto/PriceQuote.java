package com.example.hackathoncodaro2026.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceQuote {

    private BigDecimal amount;
    private String currency;

    public PriceQuote() {
    }

    public PriceQuote(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFormattedAmount() {
        String code = currency == null || currency.isBlank() ? "PLN" : currency;
        if (amount == null) {
            return "0.00 " + code;
        }
        return amount.setScale(2, RoundingMode.HALF_UP) + " " + code;
    }
}
