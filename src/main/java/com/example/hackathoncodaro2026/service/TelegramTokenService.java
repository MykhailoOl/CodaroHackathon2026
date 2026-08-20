package com.example.hackathoncodaro2026.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramTokenService {

    private static final Duration TTL = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Issued> issued = new ConcurrentHashMap<>();

    public Issued issue(String username) {
        prune();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Issued value = new Issued(token, username, Instant.now().plus(TTL));
        issued.put(token, value);
        return value;
    }

    public Issued resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Issued value = issued.get(token);
        if (value == null || value.expiresAt().isBefore(Instant.now())) {
            issued.remove(token);
            return null;
        }
        return value;
    }

    private void prune() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Issued>> iterator = issued.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Issued> entry = iterator.next();
            if (entry.getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
    }

    public record Issued(String token, String username, Instant expiresAt) {
    }
}
