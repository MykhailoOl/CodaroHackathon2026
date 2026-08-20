package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.voice.invite.LoggingInvitationMailer;
import com.example.hackathoncodaro2026.voice.sms.LoggingSmsClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoiceBookingWebTests {

    private static final String SECRET = "change-me-tool-webhook-secret";
    private static final String CHAPEL_EVENING = "chapel tomorrow evening";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private LoggingSmsClient loggingSmsClient;

    @Autowired
    private LoggingInvitationMailer loggingInvitationMailer;

    @BeforeEach
    void clearSmsLog() {
        loggingSmsClient.clear();
        loggingInvitationMailer.clear();
    }

    @Test
    void checkAvailabilityRequiresBearerSecret() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"chapel tomorrow evening"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkAvailabilityRejectsWrongSecret() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"chapel tomorrow evening"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkAvailabilityUsesSameIntentTextAsChatbot() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text":"chapel tomorrow evening",
                                  "partySize":2,
                                  "language":"en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.slots[0].slotId").isString())
                .andExpect(jsonPath("$.slots[0].displayLabel").isString())
                .andExpect(jsonPath("$.slots[0].resourceId").isNumber())
                .andExpect(jsonPath("$.slots[0].start").isString())
                .andExpect(jsonPath("$.slots[0].end").isString())
                .andExpect(jsonPath("$.slots[0].partySize").value(2));
    }

    @Test
    void createBookingPersistsToDatabaseAndLogsSms() throws Exception {
        String slotId = firstSlotId(CHAPEL_EVENING);

        mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId":"%s",
                                  "playerName":"Anna Kowalska",
                                  "playerPhone":"+48 600 111 222",
                                  "language":"en"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").isString())
                .andExpect(jsonPath("$.confirmationLine").isString())
                .andExpect(jsonPath("$.smsStatus").value("logged"))
                .andExpect(jsonPath("$.inviteUrl").value(containsString("/voice/invite/")));

        assertThat(reservationRepository.findAll())
                .anyMatch(this::occupyingTennisForAnna);
        assertThat(loggingSmsClient.sent()).isNotEmpty();
        assertThat(loggingSmsClient.sent().getFirst().to()).contains("600");
        assertThat(loggingSmsClient.sent().getFirst().body()).contains("Courtly");
        assertThat(loggingSmsClient.sent().getFirst().body()).contains("/voice/invite/");
    }

    @Test
    void createBookingAcceptsIntentResourceFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"chapel tomorrow evening","language":"en"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Number resourceId = JsonPath.read(body, "$.slots[0].resourceId");
        String start = JsonPath.read(body, "$.slots[0].start");
        String end = JsonPath.read(body, "$.slots[0].end");

        mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceId":%s,
                                  "start":"%s",
                                  "end":"%s",
                                  "partySize":2,
                                  "playerName":"Intent Caller",
                                  "playerPhone":"+48 600 333 444",
                                  "language":"en"
                                }
                                """.formatted(resourceId, start, end)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").isString());
    }

    @Test
    void createBookingRejectsTakenSlot() throws Exception {
        String slotId = firstSlotId(CHAPEL_EVENING);
        String body = """
                {
                  "slotId":"%s",
                  "playerName":"First Caller",
                  "playerPhone":"+48 600 111 001",
                  "language":"en"
                }
                """.formatted(slotId);

        mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("First Caller", "Second Caller").replace("111 001", "111 002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slot_no_longer_available"));
    }

    @Test
    void composedLegacyFieldsStillReachIntent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sport":"chapel",
                                  "preferredDay":"tomorrow",
                                  "partOfDay":"evening",
                                  "language":"en"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        List<String> labels = JsonPath.read(result.getResponse().getContentAsString(), "$.slots[*].displayLabel");
        assertThat(labels).isNotEmpty();
        assertThat(labels.getFirst()).containsIgnoringCase("chapel");
    }

    @Test
    void toolCatalogIsPublicPlaceholderForElevenLabs() throws Exception {
        mockMvc.perform(get("/api/voice/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[0].name").value("check_availability"))
                .andExpect(jsonPath("$.tools[1].name").value("create_booking"))
                .andExpect(jsonPath("$.phoneNumberWiring.status").value("placeholder"))
                .andExpect(jsonPath("$.agentProvisioning.status").value("placeholder"))
                .andExpect(jsonPath("$.sipWiring.status").value("placeholder"));
    }

    @Test
    void provisionWithoutSecretIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/voice/provision"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionWithoutElevenLabsKeyStaysPlaceholder() throws Exception {
        mockMvc.perform(post("/api/voice/provision")
                        .header("Authorization", "Bearer " + SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("placeholder"))
                .andExpect(jsonPath("$.createdRemotely").value(false))
                .andExpect(jsonPath("$.sip.status").value("placeholder"))
                .andExpect(jsonPath("$.tools[0].name").value("check_availability"));
    }

    @Test
    void phoneBookingInviteCollectsEmailAndLogsCalendarInvitation() throws Exception {
        String slotId = firstSlotId(CHAPEL_EVENING);
        MvcResult booked = mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId":"%s",
                                  "playerName":"Anna Kowalska",
                                  "playerPhone":"+48 600 111 222",
                                  "language":"en"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isOk())
                .andReturn();

        String inviteUrl = JsonPath.read(booked.getResponse().getContentAsString(), "$.inviteUrl");
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        mockMvc.perform(get("/voice/invite/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email for the invitation")));

        mockMvc.perform(post("/voice/invite/" + token)
                        .with(csrf())
                        .param("email", "anna@example.com"))
                .andExpect(status().is3xxRedirection());

        assertThat(loggingInvitationMailer.sent())
                .anyMatch(sent -> sent.to().equals("anna@example.com"));
        assertThat(reservationRepository.findByInviteToken(token))
                .map(Reservation::getInviteEmail)
                .contains("anna@example.com");

        mockMvc.perform(get("/voice/invite/" + token + "/calendar.ics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")));
    }

    private String firstSlotId(String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"%s","language":"en"}
                                """.formatted(text)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> slotIds = JsonPath.read(result.getResponse().getContentAsString(), "$.slots[*].slotId");
        assertThat(slotIds).isNotEmpty();
        return slotIds.getFirst();
    }

    private boolean occupyingTennisForAnna(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.PENDING
                && reservation.getUser().getFullName().contains("Anna")
                && reservation.getResource().getType().name().equals("CHAPEL");
    }
}
