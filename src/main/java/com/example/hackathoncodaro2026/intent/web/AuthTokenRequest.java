package com.example.hackathoncodaro2026.intent.web;

import jakarta.validation.constraints.NotBlank;

public record AuthTokenRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
