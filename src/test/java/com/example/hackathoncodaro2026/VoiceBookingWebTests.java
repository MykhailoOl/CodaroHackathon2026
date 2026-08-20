package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.voice.sms.LoggingSmsClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingSmsClient smsClient;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void clearSms() {
        smsClient.clear();
    }

    @Test
    void checkAvailabilityRequiresTheWebhookSecret() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"burial, dad died yesterday\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkAvailabilityAsksForTheDateOfDeath() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"burial for 20 mourners\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.callerSafeMessage", containsString("date of death")));
    }

    @Test
    void checkAvailabilityProposesOneWillowChapelTime() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"burial, dad died yesterday, about 20 mourners\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].displayLabel", containsString("Willow Chapel")))
                .andExpect(jsonPath("$.slots[0].displayLabel", containsString("EverRest Warsaw")))
                .andExpect(jsonPath("$.slots[0].slotId").isNotEmpty());
    }

    @Test
    void skipCountProposesTheNextEarliestTime() throws Exception {
        JsonNode first = check("burial, dad died yesterday, about 20 mourners", 0);
        JsonNode second = check("burial, dad died yesterday, about 20 mourners", 1);
        assertThat(first.get("start").asText()).isNotEqualTo(second.get("start").asText());
        assertThat(first.get("displayLabel").asText()).contains("Willow Chapel");
        assertThat(second.get("displayLabel").asText()).contains("Willow Chapel");
    }

    @Test
    void cremationProposesTheCremationSuite() throws Exception {
        mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"cremation, dad died yesterday, about 20 mourners\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].displayLabel", containsString("Cremation Suite")));
    }

    @Test
    void createBookingSendsSmsAndInviteStaysPending() throws Exception {
        JsonNode slot = check("burial, dad died yesterday, about 20 mourners", 0);
        String body = """
                {
                  "slotId": "%s",
                  "deceasedFullName": "Jan Kowalski",
                  "playerName": "Anna Kowalska",
                  "playerPhone": "+48585006115",
                  "partySize": 20
                }
                """.formatted(slot.get("slotId").asText());
        String response = mockMvc.perform(post("/api/voice/tools/create-booking")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andExpect(jsonPath("$.inviteUrl", containsString("/voice/invite/")))
                .andExpect(jsonPath("$.confirmationLine", containsString("Willow Chapel")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode booked = objectMapper.readTree(response);
        Long id = Long.valueOf(booked.get("bookingId").asText());
        Reservation reservation = reservationRepository.findById(id).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getInviteToken()).isNotBlank();
        assertThat(smsClient.sent()).hasSize(1);
        assertThat(smsClient.sent().getFirst().to()).isEqualTo("+48585006115");
        assertThat(smsClient.sent().getFirst().body()).contains("/voice/invite/" + reservation.getInviteToken());
        assertThat(smsClient.sent().getFirst().body()).contains("Jan Kowalski");
        assertThat(smsClient.sent().getFirst().body()).contains("pending");
        assertThat(smsClient.sent().getFirst().body()).contains("Willow Chapel");
        assertThat(reservation.getBookingSource()).isEqualTo("PHONE");

        mockMvc.perform(get("/voice/invite/" + reservation.getInviteToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add to calendar")))
                .andExpect(content().string(containsString("Willow Chapel")))
                .andExpect(content().string(containsString("calendar.google.com")))
                .andExpect(content().string(containsString("director still confirms")));
        assertThat(reservationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
    }

    private JsonNode check(String text, int skipCount) throws Exception {
        String response = mockMvc.perform(post("/api/voice/tools/check-availability")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\",\"skipCount\":" + skipCount + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("slots").get(0);
    }
}
