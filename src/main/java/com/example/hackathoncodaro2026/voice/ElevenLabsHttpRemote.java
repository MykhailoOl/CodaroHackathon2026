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
        List<String> required = new ArrayList<>();
        request.bodyFields().forEach((name, description) -> {
            propertiesMap.put(name, Map.of("type", "string", "description", description));
            required.add(name);
        });
        Map<String, Object> bodySchema = new LinkedHashMap<>();
        bodySchema.put("type", "object");
        bodySchema.put("description", request.description());
        bodySchema.put("properties", propertiesMap);
        bodySchema.put("required", required);

        Map<String, Object> apiSchema = new LinkedHashMap<>();
        apiSchema.put("url", request.url());
        apiSchema.put("method", "POST");
        apiSchema.put("request_headers", Map.of(
                "Authorization", "Bearer " + request.bearerToken(),
                "Content-Type", "application/json"
        ));
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
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("prompt", request.systemPrompt());
        prompt.put("llm", "gemini-2.0-flash");
        prompt.put("tool_ids", request.toolIds());
        prompt.put("timezone", request.timezone());

        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("first_message", request.firstMessage());
        agent.put("language", "en");
        agent.put("prompt", prompt);

        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("agent", agent);
        if (request.voiceId() != null && !request.voiceId().isBlank()) {
            conversation.put("tts", Map.of("voice_id", request.voiceId()));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("conversation_config", conversation);
        return requireId(post("/v1/convai/agents/create", payload), "agent_id", "id");
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "sip_trunk");
        payload.put("phone_number", request.e164());
        payload.put("label", request.label());
        payload.put("agent_id", request.agentId());
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
            patch("/v1/convai/phone-numbers/" + existing, Map.of("agent_id", request.agentId()));
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
