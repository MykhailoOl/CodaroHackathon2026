package com.example.hackathoncodaro2026.voice;

public class VoiceToolException extends RuntimeException {

    private final String code;
    private final int status;

    public VoiceToolException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public static VoiceToolException unauthorized() {
        return new VoiceToolException("unauthorized", "Tool webhook secret is missing or invalid.", 401);
    }

    public static VoiceToolException validation(String message) {
        return new VoiceToolException("validation_failed", message, 400);
    }

    public static VoiceToolException slotGone(String message) {
        return new VoiceToolException("slot_no_longer_available", message, 409);
    }

    public static VoiceToolException internal(String message) {
        return new VoiceToolException("internal", message, 500);
    }
}
