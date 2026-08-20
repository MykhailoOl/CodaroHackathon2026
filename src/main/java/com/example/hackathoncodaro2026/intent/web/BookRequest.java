package com.example.hackathoncodaro2026.intent.web;

import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BookRequest(
        @NotNull Long resourceId,
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @Min(1) Integer partySize,
        @NotNull PaymentMethod paymentMethod
) {
}
