package com.example.hackathoncodaro2026.intent.api;

import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.intent.security.TokenService;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntentApiTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Test
    void suggestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/intent/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"chapel tomorrow evening\",\"partySize\":2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestWithValidTokenSucceeds() throws Exception {
        ensureUser("intent_api_user", "intent.api.user@example.com", "Intent Api User");
        String token = obtainToken("intent_api_user", "Password1");

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"chapel tomorrow evening\",\"partySize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec").exists())
                .andExpect(jsonPath("$.parserUsed").exists())
                .andExpect(jsonPath("$.suggestions").isArray());
    }


    @Test
    void aRequestStatingADeathCarriesTheDerivedWindow() throws Exception {
        ensureUser("intent_api_family", "intent.api.family@example.com", "Intent Api Family");
        String token = obtainToken("intent_api_family", "Password1");
        LocalDate today = LocalDate.now(WARSAW);

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"my father died yesterday, orthodox service, "
                                + "we have the death certificate, about 40 mourners\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts.dateOfDeath").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.facts.rite").value("ORTHODOX"))
                .andExpect(jsonPath("$.facts.mourners").value(40))
                .andExpect(jsonPath("$.window.rite").value("ORTHODOX"))
                .andExpect(jsonPath("$.window.feasible").value(true))
                .andExpect(jsonPath("$.window.earliest").value(today.toString()))
                // Orthodox: within three days of the death, which was yesterday.
                .andExpect(jsonPath("$.window.latest").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.window.derivation").isNotEmpty())
                .andExpect(jsonPath("$.window.decisionBy").exists())
                // The derived window replaces whatever dates the text implied.
                .andExpect(jsonPath("$.spec.dayFrom").value(today.toString()))
                .andExpect(jsonPath("$.spec.dayTo").value(today.plusDays(2).toString()))
                // The stated mourner count stands in for an unsent party size.
                .andExpect(jsonPath("$.spec.partySize").value(40));
    }

    @Test
    void aRequestStatingNoDeathBehavesExactlyAsBefore() throws Exception {
        ensureUser("intent_api_plain", "intent.api.plain@example.com", "Intent Api Plain");
        String token = obtainToken("intent_api_plain", "Password1");

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"chapel tomorrow evening\",\"partySize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").doesNotExist())
                .andExpect(jsonPath("$.facts").doesNotExist())
                .andExpect(jsonPath("$.spec.dayFrom").value(LocalDate.now(WARSAW).plusDays(1).toString()));
    }

    @Test
    void aDatePreferenceOutsideTheWindowIsOverriddenByIt() throws Exception {
        ensureUser("intent_api_pref", "intent.api.pref@example.com", "Intent Api Pref");
        String token = obtainToken("intent_api_pref", "Password1");
        LocalDate today = LocalDate.now(WARSAW);

        // A Jewish rite closes the window tomorrow; asking for next week cannot open it.
        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"mum died today, jewish, we have the death certificate, "
                                + "chapel next week\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.latest").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.spec.dayTo").value(today.plusDays(1).toString()));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        ensureUser("intent_api_tamper", "intent.api.tamper@example.com", "Intent Api Tamper");
        String token = obtainToken("intent_api_tamper", "Password1");
        // Tamper the FIRST character of the signature, not the last: base64url discards
        // the trailing bits of the final character, so flipping it can decode to the very
        // same bytes and leave the token valid. That made this test flake.
        int dot = token.indexOf('.');
        char first = token.charAt(dot + 1);
        String tampered = token.substring(0, dot + 1)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(dot + 2);

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"chapel\",\"partySize\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        ensureUser("intent_api_expired", "intent.api.expired@example.com", "Intent Api Expired");
        TokenService.IssuedToken issued = tokenService.issue("intent_api_expired", Instant.now().minusSeconds(10));

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + issued.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"chapel\",\"partySize\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookReturnsCleanErrorWhenSlotAlreadyTaken() throws Exception {
        ensureUser("intent_book_first", "intent.book.first@example.com", "Intent Book First");
        ensureUser("intent_book_second", "intent.book.second@example.com", "Intent Book Second");
        String tokenFirst = obtainToken("intent_book_first", "Password1");
        String tokenSecond = obtainToken("intent_book_second", "Password1");

        SportResource resource = sportResourceRepository.findAll().stream()
                .filter(r -> r.getCapacity() == 1 && r.isEnabled())
                .findFirst()
                .orElseThrow();
        LocalDate date = LocalDate.now(WARSAW).plusDays(20);
        LocalTime startTime = resource.getOpeningTime().plusHours(1);
        LocalDateTime start = LocalDateTime.of(date, startTime);
        LocalDateTime end = start.plusHours(1);
        String partySizeJson = resource.requiresPartySize()
                ? "\"partySize\":" + resource.getMinPartySize() + ","
                : "";

        String body = String.format(
                "{\"resourceId\":%d,%s\"start\":\"%s\",\"end\":\"%s\",\"paymentMethod\":\"CASH\"}",
                resource.getId(), partySizeJson, ISO_LOCAL.format(start), ISO_LOCAL.format(end)
        );

        mockMvc.perform(post("/api/intent/book")
                        .header("Authorization", "Bearer " + tokenFirst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").exists());

        mockMvc.perform(post("/api/intent/book")
                        .header("Authorization", "Bearer " + tokenSecond)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").exists());
    }

    private User ensureUser(String username, String email, String fullName) {
        return userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setFullName(fullName);
            request.setPhone("+48 555 010 099");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            return userService.register(request);
        });
    }

    private String obtainToken(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }
}
