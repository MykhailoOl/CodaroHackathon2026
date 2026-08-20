package com.example.hackathoncodaro2026.intent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Component
public class TokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Duration DEFAULT_TTL = Duration.ofHours(12);

    private final byte[] secretKey;

    public TokenService(@Value("${intent.auth.secret:}") String configuredSecret) {
        this.secretKey = (configuredSecret == null || configuredSecret.isBlank())
                ? randomSecret()
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public IssuedToken issue(String username) {
        return issue(username, Instant.now().plus(DEFAULT_TTL));
    }

    public IssuedToken issue(String username, Instant expiresAt) {
        String payload = username + "|" + expiresAt.getEpochSecond();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signature = hmac(payloadBytes);
        String token = base64Url(payloadBytes) + "." + base64Url(signature);
        return new IssuedToken(token, expiresAt);
    }

    public Optional<VerifiedToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] providedSignature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(token.substring(0, dot));
            providedSignature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        byte[] expectedSignature = hmac(payloadBytes);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            return Optional.empty();
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        int sep = payload.lastIndexOf('|');
        if (sep <= 0) {
            return Optional.empty();
        }
        String username = payload.substring(0, sep);
        long expiryEpoch;
        try {
            expiryEpoch = Long.parseLong(payload.substring(sep + 1));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.ofEpochSecond(expiryEpoch);
        if (Instant.now().isAfter(expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedToken(username, expiresAt));
    }

    private byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign token", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record VerifiedToken(String username, Instant expiresAt) {
    }
}
