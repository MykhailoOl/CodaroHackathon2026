package com.example.hackathoncodaro2026.voice;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class VoiceToolAuthenticator {

    private final VoiceProperties properties;

    public VoiceToolAuthenticator(VoiceProperties properties) {
        this.properties = properties;
    }

    public void requireSecret(String authorization, String toolSecretHeader) {
        String expected = properties.getToolWebhookSecret();
        if (!StringUtils.hasText(expected)) {
            throw VoiceToolException.unauthorized();
        }
        String provided = extractBearer(authorization);
        if (!StringUtils.hasText(provided) && StringUtils.hasText(toolSecretHeader)) {
            provided = toolSecretHeader.trim();
        }
        if (!expected.equals(provided)) {
            throw VoiceToolException.unauthorized();
        }
    }

    private String extractBearer(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
