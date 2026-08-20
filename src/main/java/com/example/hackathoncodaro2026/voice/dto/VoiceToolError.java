package com.example.hackathoncodaro2026.voice.dto;

public class VoiceToolError {

    private String code;
    private String callerSafeMessage;

    public VoiceToolError() {
    }

    public VoiceToolError(String code, String callerSafeMessage) {
        this.code = code;
        this.callerSafeMessage = callerSafeMessage;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCallerSafeMessage() {
        return callerSafeMessage;
    }

    public void setCallerSafeMessage(String callerSafeMessage) {
        this.callerSafeMessage = callerSafeMessage;
    }
}
