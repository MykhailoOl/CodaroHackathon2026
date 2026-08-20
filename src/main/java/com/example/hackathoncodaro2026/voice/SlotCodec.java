package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.model.enums.ReservationKind;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.HexFormat;

public class SlotCodec {

    public record DecodedSlot(
            long resourceId,
            LocalDate date,
            LocalTime start,
            int durationHours,
            ReservationKind kind
    ) {
    }

    private final String secret;

    public SlotCodec(String secret) {
        this.secret = secret == null || secret.isBlank() ? "change-me-tool-webhook-secret" : secret;
    }

    public String encode(long resourceId, LocalDate date, LocalTime start, int durationHours, ReservationKind kind) {
        String payload = resourceId + "|" + date + "|" + start + "|" + durationHours + "|" + kind.name();
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "slot_" + body + "." + hmac(body);
    }

    public DecodedSlot decode(String slotId) {
        if (slotId == null || !slotId.startsWith("slot_") || !slotId.contains(".")) {
            throw VoiceToolException.validation("That slot is not valid. Please check availability again.");
        }
        String raw = slotId.substring("slot_".length());
        int dot = raw.lastIndexOf('.');
        String body = raw.substring(0, dot);
        String signature = raw.substring(dot + 1);
        if (!hmac(body).equals(signature)) {
            throw VoiceToolException.validation("That slot is not valid. Please check availability again.");
        }
        String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|");
        if (parts.length != 5) {
            throw VoiceToolException.validation("That slot is not valid. Please check availability again.");
        }
        try {
            return new DecodedSlot(
                    Long.parseLong(parts[0]),
                    LocalDate.parse(parts[1]),
                    LocalTime.parse(parts[2]),
                    Integer.parseInt(parts[3]),
                    ReservationKind.valueOf(parts[4])
            );
        } catch (RuntimeException ex) {
            throw VoiceToolException.validation("That slot is not valid. Please check availability again.");
        }
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign slot id", ex);
        }
    }
}
