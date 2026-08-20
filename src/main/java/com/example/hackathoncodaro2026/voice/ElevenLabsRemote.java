package com.example.hackathoncodaro2026.voice;

import java.util.List;
import java.util.Map;

public interface ElevenLabsRemote {

    record WebhookToolRequest(
            String name,
            String description,
            String url,
            String bearerToken,
            Map<String, String> bodyFields
    ) {
    }

    record AgentRequest(
            String name,
            String firstMessage,
            String systemPrompt,
            List<String> toolIds,
            String voiceId,
            String timezone
    ) {
    }

    record SipNumberRequest(
            String e164,
            String label,
            String agentId,
            String username,
            String password
    ) {
    }

    String createWebhookTool(WebhookToolRequest request);

    String createAgent(AgentRequest request);

    void updateAgent(String agentId, AgentRequest request);

    String findAgentId(String name);

    String importSipNumber(SipNumberRequest request);
}
