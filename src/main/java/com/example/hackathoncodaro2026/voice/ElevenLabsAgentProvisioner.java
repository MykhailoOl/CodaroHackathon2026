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
            spec.put("sip", sipStatus(null, null));
            spec.put("sms", smsStatus());
            return spec;
        }
        if (!isPublicHttps(base)) {
            spec.put("status", "placeholder");
            spec.put("createdRemotely", false);
            spec.put("agentId", blankToNull(properties.getElevenlabs().getAgentId()));
            spec.put("nextStep", "Set PUBLIC_BASE_URL to the HTTPS tunnel. ElevenLabs cannot reach localhost.");
            spec.put("sip", sipStatus(null, null));
            spec.put("sms", smsStatus());
            return spec;
        }

        String agentId = blankToNull(properties.getElevenlabs().getAgentId());
        if (agentId == null) {
            agentId = elevenLabsRemote.findAgentId("EverRest phone receptionist");
        }
        if (agentId == null) {
            String bearer = properties.getToolWebhookSecret();
            String checkId = elevenLabsRemote.createWebhookTool(new ElevenLabsRemote.WebhookToolRequest(
                    "check_availability",
                    "Propose the earliest ceremony inside the legal window. Pass the family's words as text. Never invent times. Read displayLabel verbatim.",
                    base + "/api/voice/tools/check-availability",
                    bearer,
                    Map.of(
                            "text", "family's words, e.g. burial, dad died yesterday, about 20 mourners",
                            "partySize", "mourner count as a digit, not the word twenty",
                            "skipCount", "0 for the first proposal. Increment by 1 if they refuse that time.",
                            "language", "en"
                    )
            ));
            String bookId = elevenLabsRemote.createWebhookTool(new ElevenLabsRemote.WebhookToolRequest(
                    "create_booking",
                    "Confirm the proposed ceremony. Pass slotId from the last check_availability result.",
                    base + "/api/voice/tools/create-booking",
                    bearer,
                    Map.of(
                            "slotId", "slotId from the last proposal",
                            "resourceId", "resourceId from the last proposal, same as venueId",
                            "start", "start from the last proposal, ISO local datetime",
                            "end", "end from the last proposal, ISO local datetime",
                            "partySize", "mourner count as a digit",
                            "deceasedFullName", "name of the deceased",
                            "playerName", "name of the person calling",
                            "language", "en"
                    )
            ));
            agentId = elevenLabsRemote.createAgent(new ElevenLabsRemote.AgentRequest(
                    "EverRest phone receptionist",
                    "I'm sorry for your loss. I'm here when you are ready.",
                    systemPrompt(),
                    List.of(checkId, bookId),
                    blankToNull(properties.getElevenlabs().getVoiceId()),
                    properties.getTimezone()
            ));
            spec.put("checkToolId", checkId);
            spec.put("bookToolId", bookId);
        }

        elevenLabsRemote.updateAgent(agentId, new ElevenLabsRemote.AgentRequest(
                "EverRest phone receptionist",
                "I'm sorry for your loss. I'm here when you are ready.",
                systemPrompt(),
                List.of(),
                blankToNull(properties.getElevenlabs().getVoiceId()),
                properties.getTimezone()
        ));

        String phoneId = blankToNull(properties.getElevenlabs().getPhoneNumberId());
        String sipError = null;
        if (properties.getTelephony().sipConfigured() && phoneId == null) {
            try {
                phoneId = elevenLabsRemote.importSipNumber(new ElevenLabsRemote.SipNumberRequest(
                        properties.getTelephony().getSipFromNumber().trim(),
                        "EverRest spare DID",
                        agentId,
                        properties.getTelephony().getSipUsername(),
                        properties.getTelephony().getSipPassword()
                ));
            } catch (VoiceToolException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("409")) {
                    sipError = "This DID is already registered in ElevenLabs. Release it there or pick another spare number.";
                } else {
                    throw ex;
                }
            }
        }

        spec.put("status", "ready");
        spec.put("createdRemotely", true);
        spec.put("agentId", agentId);
        spec.put("nextStep", phoneId != null
                ? "Put ELEVENLABS_AGENT_ID and ELEVENLABS_PHONE_NUMBER_ID in .env. Call the spare number."
                : sipError != null
                ? sipError
                : "Put ELEVENLABS_AGENT_ID=" + agentId + " in .env. Add SIP credentials and POST /api/voice/provision again.");
        spec.put("sip", sipStatus(phoneId, sipError));
        spec.put("sms", smsStatus());
        return spec;
    }

    private Map<String, Object> baseSpec(String base) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("name", "EverRest phone receptionist");
        spec.put("firstMessage", "I'm sorry for your loss. I'm here when you are ready.");
        spec.put("systemPrompt", systemPrompt());
        spec.put("voiceIdConfigured", notBlank(properties.getElevenlabs().getVoiceId()));
        spec.put("tools", List.of(
                Map.of("name", "check_availability", "url", base + "/api/voice/tools/check-availability", "method", "POST"),
                Map.of("name", "create_booking", "url", base + "/api/voice/tools/create-booking", "method", "POST")
        ));
        spec.put("authorization", "Bearer TOOL_WEBHOOK_SECRET");
        return spec;
    }

    private Map<String, Object> sipStatus(String phoneNumberId, String conflict) {
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
        sip.put("nextStep", conflict != null
                ? conflict
                : ready
                ? "Inbound SIP is assigned. Point Telnyx at sip.rtc.elevenlabs.io:5060 TCP."
                : "Add SIP_FROM_NUMBER, SIP_USERNAME, SIP_PASSWORD and POST /api/voice/provision.");
        return sip;
    }

    private Map<String, Object> smsStatus() {
        VoiceProperties.Sms sms = properties.getSms();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", sms.getProvider());
        body.put("telnyxConfigured", notBlank(sms.getTelnyxApiKey()) && notBlank(sms.getTelnyxMessagingProfileId()));
        body.put("nextStep", notBlank(sms.getTelnyxApiKey()) && notBlank(sms.getTelnyxMessagingProfileId())
                ? "SMS sends via Telnyx with the alphanumeric sender after a booking."
                : "Add TELNYX_API_KEY and TELNYX_MESSAGING_PROFILE_ID to send SMS via Telnyx.");
        return body;
    }

    private String systemPrompt() {
        return """
                You are a quiet funeral-home receptionist at EverRest in Warsaw. The caller is grieving. Speak English, slowly, in a low sad voice.
                Keep every reply to one short sentence, then wait. Never sound cheerful, brisk, or like a shop. No puns. No pep.
                If they pause or cry, stay silent and wait. Do not fill the silence. Do not rush them.
                Never invent times. Propose one ceremony, not a menu.
                Ask gently for the date of death, burial or cremation, about how many mourners, and the name of the person who died.
                Call check_availability with those facts in text. partySize is a digit. skipCount starts at 0.
                Read displayLabel verbatim. Labels already contain spoken English times. Do not convert them to digits.
                If they refuse that time, call check_availability again with skipCount increased by one, then propose the next time.
                On confirm, call create_booking with slotId from the last proposal, deceasedFullName, and the caller's name. Omit playerPhone.
                After booking, say an SMS with a calendar reminder is on the way. A director still confirms the arrangement.
                When the caller says goodbye and the request is done, say a short, quiet goodbye, then call end_call. Never hang up mid-arrangement.
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
