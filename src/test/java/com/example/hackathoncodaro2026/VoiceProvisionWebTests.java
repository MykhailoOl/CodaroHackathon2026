package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.voice.ElevenLabsRemote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.voice.elevenlabs.api-key=el-test-key",
        "app.voice.public-base-url=https://courtly.example.test",
        "app.voice.tool-webhook-secret=change-me-tool-webhook-secret"
})
@AutoConfigureMockMvc
@Transactional
class VoiceProvisionWebTests {

    private static final String SECRET = "change-me-tool-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElevenLabsRemote elevenLabsRemote;

    @Test
    void provisionCreatesElevenLabsAgentAgainstPublicTunnel() throws Exception {
        when(elevenLabsRemote.createWebhookTool(any())).thenReturn("tool-check", "tool-book");
        when(elevenLabsRemote.createAgent(any())).thenReturn("agent-courtly");

        mockMvc.perform(post("/api/voice/provision")
                        .header("Authorization", "Bearer " + SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdRemotely").value(true))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.agentId").value("agent-courtly"))
                .andExpect(jsonPath("$.sip.status").value("placeholder"));

        verify(elevenLabsRemote, never()).importSipNumber(any());
    }
}
