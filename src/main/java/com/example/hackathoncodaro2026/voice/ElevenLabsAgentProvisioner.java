package com.example.hackathoncodaro2026.voice;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElevenLabsAgentProvisioner {

    private final VoiceProperties properties;

    public ElevenLabsAgentProvisioner(VoiceProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> provision() {
        String base = trimSlash(properties.getPublicBaseUrl());
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("name", "Courtly phone receptionist");
        spec.put("firstMessage", "Hi, this is Courtly. I can check court availability and book a slot for you.");
        spec.put("systemPrompt", systemPrompt());
        spec.put("voiceIdConfigured", notBlank(properties.getElevenlabs().getVoiceId()));
        spec.put("tools", List.of(
                Map.of(
                        "name", "check_availability",
                        "url", base + "/api/voice/tools/check-availability",
                        "method", "POST"
                ),
                Map.of(
                        "name", "create_booking",
                        "url", base + "/api/voice/tools/create-booking",
                        "method", "POST"
                )
        ));
        spec.put("authorization", "Bearer TOOL_WEBHOOK_SECRET");

        boolean apiReady = properties.getElevenlabs().isConfigured();
        String storedAgentId = properties.getElevenlabs().getAgentId();
        spec.put("status", apiReady && notBlank(storedAgentId) ? "ready" : "placeholder");
        spec.put("agentId", blankToNull(storedAgentId));
        spec.put("createdRemotely", false);
        spec.put("nextStep", apiReady
                ? "Agent credentials are present. Create or patch the ConvAI agent with the tools above, then attach SIP_FROM_NUMBER."
                : "Put a rotatable ELEVENLABS_API_KEY in env and call this endpoint again, or create the agent in the ElevenLabs dashboard using this spec.");
        spec.put("sip", sipStatus());
        spec.put("sms", smsStatus());
        return spec;
    }

    private Map<String, Object> sipStatus() {
        VoiceProperties.Telephony tel = properties.getTelephony();
        Map<String, Object> sip = new LinkedHashMap<>();
        sip.put("status", tel.sipConfigured() ? "ready" : "placeholder");
        sip.put("provider", tel.getProvider());
        sip.put("terminationUri", tel.getSipTerminationUri());
        sip.put("fromNumberConfigured", notBlank(tel.getSipFromNumber()));
        sip.put("usernameConfigured", notBlank(tel.getSipUsername()));
        sip.put("passwordConfigured", notBlank(tel.getSipPassword()));
        sip.put("telnyxFqdnConfigured", notBlank(tel.getTelnyxFqdnConnectionId()));
        sip.put("nextStep", tel.sipConfigured()
                ? "Import the number in ElevenLabs from this SIP trunk and assign it to the agent."
                : "Paste Telnyx/SIP placeholders (SIP_USERNAME, SIP_PASSWORD, SIP_FROM_NUMBER). Rotate keys later; do not commit them.");
        return sip;
    }

    private Map<String, Object> smsStatus() {
        VoiceProperties.Sms sms = properties.getSms();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", sms.getProvider());
        body.put("telnyxConfigured", notBlank(sms.getTelnyxApiKey()) && notBlank(sms.getTelnyxMessagingProfileId()));
        body.put("nextStep", "Leave provider=log until TELNYX_API_KEY is rotated in. SMS still logs the invite link.");
        return body;
    }

    private String systemPrompt() {
        return """
                You are Courtly's phone receptionist for Warsaw sports facilities.
                Website booking and phone booking share the same database. Never invent free times.
                Ask for the sport, then the day, then call check_availability.
                Read displayLabel values verbatim. On confirm, call create_booking with the slotId,
                the caller's name, and system__caller_id as playerPhone.
                After booking, tell them an SMS will arrive with a link to enter email for a calendar invitation.
                If a slot is taken, offer another from a fresh check_availability call.
                """;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
