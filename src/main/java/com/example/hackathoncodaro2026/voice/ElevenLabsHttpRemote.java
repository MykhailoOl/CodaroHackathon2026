package com.example.hackathoncodaro2026.voice;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElevenLabsHttpRemote implements ElevenLabsRemote {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final VoiceProperties properties;

    public ElevenLabsHttpRemote(VoiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.elevenlabs.io")
                .build();
    }

    @Override
    public String createWebhookTool(WebhookToolRequest request) {
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        request.bodyFields().forEach((name, description) -> {
            if ("playerPhone".equals(name)) {
                Map<String, Object> phone = new LinkedHashMap<>();
                phone.put("type", "string");
                phone.put("dynamic_variable", "system__caller_id");
                propertiesMap.put(name, phone);
            } else {
                propertiesMap.put(name, Map.of("type", "string", "description", description));
            }
        });
        List<String> required = new ArrayList<>();
        if (propertiesMap.containsKey("text")) {
            required.add("text");
        } else {
            for (String name : List.of("resourceId", "start", "end")) {
                if (propertiesMap.containsKey(name)) {
                    required.add(name);
                }
            }
        }
        Map<String, Object> bodySchema = new LinkedHashMap<>();
        bodySchema.put("type", "object");
        bodySchema.put("description", request.description());
        bodySchema.put("properties", propertiesMap);
        bodySchema.put("required", required);

        Map<String, Object> apiSchema = new LinkedHashMap<>();
        apiSchema.put("url", request.url());
        apiSchema.put("method", "POST");
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + request.bearerToken());
        headers.put("Content-Type", "application/json");
        headers.put("ngrok-skip-browser-warning", "1");
        if ("create_booking".equals(request.name())) {
            headers.put("X-Caller-Id", Map.of("variable_name", "system__caller_id"));
        }
        apiSchema.put("request_headers", headers);
        apiSchema.put("request_body_schema", bodySchema);

        Map<String, Object> toolConfig = new LinkedHashMap<>();
        toolConfig.put("type", "webhook");
        toolConfig.put("name", request.name());
        toolConfig.put("description", request.description());
        toolConfig.put("api_schema", apiSchema);

        Map<String, Object> payload = Map.of("tool_config", toolConfig);
        return requireId(post("/v1/convai/tools", payload), "id");
    }

    @Override
    public String createAgent(AgentRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("conversation_config", conversationConfig(request, request.toolIds()));
        return requireId(post("/v1/convai/agents/create", payload), "agent_id", "id");
    }

    @Override
    public void updateAgent(String agentId, AgentRequest request) {
        List<String> toolIds = request.toolIds() == null || request.toolIds().isEmpty()
                ? existingToolIds(agentId)
                : request.toolIds();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_config", conversationConfig(request, toolIds));
        patch("/v1/convai/agents/" + agentId, payload);
    }

    private Map<String, Object> conversationConfig(AgentRequest request, List<String> toolIds) {
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("prompt", request.systemPrompt());
        prompt.put("llm", "qwen36-35b-a3b");
        prompt.put("temperature", 0.3);
        prompt.put("tool_ids", toolIds == null ? List.of() : toolIds);
        prompt.put("timezone", request.timezone());
        prompt.put("built_in_tools", Map.of(
                "end_call", Map.of(
                        "name", "end_call",
                        "description",
                        "End the call after a short goodbye once the caller is done. Never hang up mid-booking or right after a question."
                )
        ));

        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("first_message", request.firstMessage());
        agent.put("language", "en");
        agent.put("disable_first_message_interruptions", true);
        agent.put("prompt", prompt);

        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("model_id", "eleven_flash_v2");
        tts.put("expressive_mode", false);
        tts.put("stability", 0.82);
        tts.put("similarity_boost", 0.75);
        tts.put("speed", 0.86);
        tts.put("optimize_streaming_latency", 3);
        tts.put("voice_id", request.voiceId() == null || request.voiceId().isBlank()
                ? "EXAVITQu4vr4xnSDxMaL"
                : request.voiceId());

        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("agent", agent);
        conversation.put("tts", tts);
        conversation.put("asr", Map.of(
                "provider", "scribe_realtime",
                "quality", "high",
                "user_input_audio_format", "pcm_16000"
        ));
        conversation.put("turn", Map.of(
                "turn_timeout", 8,
                "mode", "turn",
                "turn_eagerness", "patient",
                "turn_model", "turn_v2"
        ));
        conversation.put("language_presets", Map.of());
        return conversation;
    }

    private List<String> existingToolIds(String agentId) {
        Object body = get("/v1/convai/agents/" + agentId);
        if (!(body instanceof Map<?, ?> root)) {
            return List.of();
        }
        Object conv = root.get("conversation_config");
        if (!(conv instanceof Map<?, ?> convMap)) {
            return List.of();
        }
        Object agent = convMap.get("agent");
        if (!(agent instanceof Map<?, ?> agentMap)) {
            return List.of();
        }
        Object prompt = agentMap.get("prompt");
        if (!(prompt instanceof Map<?, ?> promptMap)) {
            return List.of();
        }
        Object ids = promptMap.get("tool_ids");
        if (!(ids instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object id : list) {
            if (id != null && !id.toString().isBlank()) {
                out.add(id.toString());
            }
        }
        return out;
    }

    @Override
    public String findAgentId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Object body = get("/v1/convai/agents");
        for (Map<String, Object> item : asMaps(body, "agents")) {
            String candidate = stringOf(item.get("name"), item.get("agent_name"));
            if (name.equals(candidate)) {
                String id = stringOf(item.get("agent_id"), item.get("id"));
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    @Override
    public String importSipNumber(SipNumberRequest request) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("username", request.username());
        credentials.put("password", request.password());

        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("address", "sip.telnyx.com");
        outbound.put("transport", "tcp");
        outbound.put("credentials", credentials);

        Map<String, Object> inbound = new LinkedHashMap<>();
        inbound.put("media_encryption", "allowed");
        inbound.put("remote_domains", List.of("sip.telnyx.com"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "sip_trunk");
        payload.put("phone_number", request.e164());
        payload.put("label", request.label());
        payload.put("agent_id", request.agentId());
        payload.put("inbound_trunk_config", inbound);
        payload.put("outbound_trunk_config", outbound);
        try {
            return requireId(post("/v1/convai/phone-numbers", payload), "phone_number_id", "id");
        } catch (VoiceToolException ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("409")) {
                throw ex;
            }
            String existing = findPhoneNumberId(request.e164());
            if (existing == null) {
                throw ex;
            }
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("agent_id", request.agentId());
            update.put("inbound_trunk_config", inbound);
            patch("/v1/convai/phone-numbers/" + existing, update);
            return existing;
        }
    }

    private String findPhoneNumberId(String e164) {
        Object body = get("/v1/convai/phone-numbers");
        for (Map<String, Object> item : asMaps(body, "phone_numbers", "data")) {
            if (e164.equals(stringOf(item.get("phone_number")))) {
                return stringOf(item.get("phone_number_id"), item.get("id"));
            }
        }
        return null;
    }

    private Map<String, Object> post(String path, Object body) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(path)
                    .header("xi-api-key", properties.getElevenlabs().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP);
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException ex) {
            throw VoiceToolException.remote("ElevenLabs " + ex.getStatusCode().value() + " " + trimBody(ex.getResponseBodyAsString()));
        }
    }

    private Object get(String path) {
        try {
            return restClient.get()
                    .uri(path)
                    .header("xi-api-key", properties.getElevenlabs().getApiKey())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException ex) {
            throw VoiceToolException.remote("ElevenLabs " + ex.getStatusCode().value() + " " + trimBody(ex.getResponseBodyAsString()));
        }
    }

    private void patch(String path, Object body) {
        try {
            restClient.patch()
                    .uri(path)
                    .header("xi-api-key", properties.getElevenlabs().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw VoiceToolException.remote("ElevenLabs " + ex.getStatusCode().value() + " " + trimBody(ex.getResponseBodyAsString()));
        }
    }

    private List<Map<String, Object>> asMaps(Object body, String... keys) {
        if (body instanceof List<?> list) {
            return maps(list);
        }
        if (body instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object nested = map.get(key);
                if (nested instanceof List<?> list) {
                    return maps(list);
                }
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String stringOf(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String requireId(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        throw VoiceToolException.remote("ElevenLabs response did not include an id.");
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }
}
