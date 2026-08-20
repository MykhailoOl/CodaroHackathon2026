package com.example.hackathoncodaro2026.intent.web;

import java.time.LocalDateTime;

public record AuthTokenResponse(String token, LocalDateTime expiresAt, String displayName) {
}
