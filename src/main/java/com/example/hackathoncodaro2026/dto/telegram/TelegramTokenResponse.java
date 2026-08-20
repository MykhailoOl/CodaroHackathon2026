package com.example.hackathoncodaro2026.dto.telegram;

import java.time.Instant;

public record TelegramTokenResponse(
        String token,
        String displayName,
        String username,
        Instant expiresAt,
        boolean phoneRequired
) {
}
