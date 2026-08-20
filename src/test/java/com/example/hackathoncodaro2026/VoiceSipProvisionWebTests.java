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
        "app.voice.elevenlabs.agent-id=agent-courtly",
        "app.voice.public-base-url=https://courtly.example.test",
        "app.voice.tool-webhook-secret=change-me-tool-webhook-secret",
        "app.voice.telephony.sip-from-number=+48100100200",
        "app.voice.telephony.sip-username=sip-user",
        "app.voice.telephony.sip-password=sip-pass"
})
@AutoConfigureMockMvc
@Transactional
class VoiceSipProvisionWebTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElevenLabsRemote elevenLabsRemote;

    @Test
    void provisionImportsSipNumberOntoExistingAgent() throws Exception {
        when(elevenLabsRemote.importSipNumber(any())).thenReturn("phone-1");

        mockMvc.perform(post("/api/voice/provision")
                        .header("Authorization", "Bearer change-me-tool-webhook-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdRemotely").value(true))
                .andExpect(jsonPath("$.agentId").value("agent-courtly"))
                .andExpect(jsonPath("$.sip.status").value("ready"))
                .andExpect(jsonPath("$.sip.phoneNumberId").value("phone-1"));

        verify(elevenLabsRemote, never()).createAgent(any());
    }
}
