package com.example.hackathoncodaro2026.voice;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElevenLabsAgentProvisioner {

    private final VoiceProperties properties;
    private final ElevenLabsRemote elevenLabsRemote;

    public ElevenLabsAgentProvisioner(VoiceProperties properties, ElevenLabsRemote elevenLabsRemote) {
        this.properties = properties;
        this.elevenLabsRemote = elevenLabsRemote;
    }

    public Map<String, Object> provision() {
        String base = trimSlash(properties.getPublicBaseUrl());
        Map<String, Object> spec = baseSpec(base);
        if (!properties.getElevenlabs().isConfigured()) {
            spec.put("status", "placeholder");
            spec.put("createdRemotely", false);
            spec.put("agentId", blankToNull(properties.getElevenlabs().getAgentId()));
            spec.put("nextStep", "Add ELEVENLABS_API_KEY and set PUBLIC_BASE_URL to the HTTPS tunnel, then POST /api/voice/provision again.");
            spec.put("sip", sipStatus(null));
            spec.put("sms", smsStatus());
            return spec;
        }
        if (!isPublicHttps(base)) {
            spec.put("status", "placeholder");
            spec.put("createdRemotely", false);
            spec.put("agentId", blankToNull(properties.getElevenlabs().getAgentId()));
            spec.put("nextStep", "Set PUBLIC_BASE_URL to the HTTPS tunnel. ElevenLabs cannot reach localhost.");
            spec.put("sip", sipStatus(null));
            spec.put("sms", smsStatus());
            return spec;
        }

        String agentId = blankToNull(properties.getElevenlabs().getAgentId());
        if (agentId == null) {
            String bearer = properties.getToolWebhookSecret();
            String checkId = elevenLabsRemote.createWebhookTool(new ElevenLabsRemote.WebhookToolRequest(
                    "check_availability",
                    "Same intent suggest as the Courtly chatbot. Pass the caller's request as text. Never invent times. Read displayLabel verbatim.",
                    base + "/api/voice/tools/check-availability",
                    bearer,
                    Map.of(
                            "text", "caller's request in their words, e.g. tennis tomorrow evening",
                            "partySize", "number of players if they said it",
                            "language", "en or pl"
                    )
            ));
            String bookId = elevenLabsRemote.createWebhookTool(new ElevenLabsRemote.WebhookToolRequest(
                    "create_booking",
                    "Same intent book as the chatbot. Pass resourceId, start, and end from the last check_availability result.",
                    base + "/api/voice/tools/create-booking",
                    bearer,
                    Map.of(
                            "resourceId", "resourceId from the chosen suggestion",
                            "start", "start from the chosen suggestion, ISO local datetime",
                            "end", "end from the chosen suggestion, ISO local datetime",
                            "slotId", "optional slotId from the same suggestion",
                            "partySize", "number of players if they said it",
                            "playerName", "caller name",
                            "playerPhone", "system caller id",
                            "language", "en or pl"
                    )
            ));
            agentId = elevenLabsRemote.createAgent(new ElevenLabsRemote.AgentRequest(
                    "Courtly phone receptionist",
                    "Hi, this is Courtly. I can check court availability and book a slot for you.",
                    systemPrompt(),
                    List.of(checkId, bookId),
                    blankToNull(properties.getElevenlabs().getVoiceId()),
                    properties.getTimezone()
            ));
            spec.put("checkToolId", checkId);
            spec.put("bookToolId", bookId);
        }

        String phoneId = blankToNull(properties.getElevenlabs().getPhoneNumberId());
        if (properties.getTelephony().sipConfigured() && phoneId == null) {
            phoneId = elevenLabsRemote.importSipNumber(new ElevenLabsRemote.SipNumberRequest(
                    properties.getTelephony().getSipFromNumber().trim(),
                    "Courtly spare DID",
                    agentId,
                    properties.getTelephony().getSipUsername(),
                    properties.getTelephony().getSipPassword()
            ));
        }

        spec.put("status", "ready");
        spec.put("createdRemotely", true);
        spec.put("agentId", agentId);
        spec.put("nextStep", phoneId == null
                ? "Put ELEVENLABS_AGENT_ID=" + agentId + " in .env. Add SIP credentials and POST /api/voice/provision again."
                : "Put ELEVENLABS_AGENT_ID and ELEVENLABS_PHONE_NUMBER_ID in .env. Call the spare number.");
        spec.put("sip", sipStatus(phoneId));
        spec.put("sms", smsStatus());
        return spec;
    }

    private Map<String, Object> baseSpec(String base) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("name", "Courtly phone receptionist");
        spec.put("firstMessage", "Hi, this is Courtly. I can check court availability and book a slot for you.");
        spec.put("systemPrompt", systemPrompt());
        spec.put("voiceIdConfigured", notBlank(properties.getElevenlabs().getVoiceId()));
        spec.put("tools", List.of(
                Map.of("name", "check_availability", "url", base + "/api/voice/tools/check-availability", "method", "POST"),
                Map.of("name", "create_booking", "url", base + "/api/voice/tools/create-booking", "method", "POST")
        ));
        spec.put("authorization", "Bearer TOOL_WEBHOOK_SECRET");
        return spec;
    }

    private Map<String, Object> sipStatus(String phoneNumberId) {
        VoiceProperties.Telephony tel = properties.getTelephony();
        Map<String, Object> sip = new LinkedHashMap<>();
        boolean ready = tel.sipConfigured() && notBlank(phoneNumberId);
        sip.put("status", ready ? "ready" : "placeholder");
        sip.put("provider", tel.getProvider());
        sip.put("terminationUri", tel.getSipTerminationUri());
        sip.put("fromNumberConfigured", notBlank(tel.getSipFromNumber()));
        sip.put("usernameConfigured", notBlank(tel.getSipUsername()));
        sip.put("passwordConfigured", notBlank(tel.getSipPassword()));
        sip.put("phoneNumberId", blankToNull(phoneNumberId));
        sip.put("nextStep", ready
                ? "Inbound SIP is assigned. Point Telnyx at sip.rtc.elevenlabs.io:5060 TCP."
                : "Add SIP_FROM_NUMBER, SIP_USERNAME, SIP_PASSWORD and POST /api/voice/provision.");
        return sip;
    }

    private Map<String, Object> smsStatus() {
        VoiceProperties.Sms sms = properties.getSms();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", sms.getProvider());
        body.put("telnyxConfigured", notBlank(sms.getTelnyxApiKey()) && notBlank(sms.getTelnyxMessagingProfileId()));
        body.put("nextStep", "SMS is log-only until Telnyx messaging keys are set.");
        return body;
    }

    private String systemPrompt() {
        return """
                You are Courtly's phone receptionist for Warsaw sports facilities.
                Phone booking uses the same intent engine as the website chatbot. Never invent free times.
                Collect the request in the caller's words (sport, day, time of day, party size) and call
                check_availability with that as text. Read displayLabel values verbatim.
                On confirm, call create_booking with resourceId, start, and end from that suggestion,
                the caller's name, and system__caller_id as playerPhone.
                After booking, tell them an SMS will arrive with a link to enter email for a calendar invitation.
                If a slot is taken, offer another from a fresh check_availability call.
                """;
    }

    private boolean isPublicHttps(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("https://")
                && !lower.contains("localhost")
                && !lower.contains("127.0.0.1");
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
