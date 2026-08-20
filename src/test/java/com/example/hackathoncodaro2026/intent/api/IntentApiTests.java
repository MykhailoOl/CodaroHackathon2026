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
                        .content("{\"text\":\"tennis tomorrow evening\",\"partySize\":2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestWithValidTokenSucceeds() throws Exception {
        ensureUser("intent_api_user", "intent.api.user@example.com", "Intent Api User");
        String token = obtainToken("intent_api_user", "Password1");

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"tennis tomorrow evening\",\"partySize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec").exists())
                .andExpect(jsonPath("$.parserUsed").exists())
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        ensureUser("intent_api_tamper", "intent.api.tamper@example.com", "Intent Api Tamper");
        String token = obtainToken("intent_api_tamper", "Password1");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"tennis\",\"partySize\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        ensureUser("intent_api_expired", "intent.api.expired@example.com", "Intent Api Expired");
        TokenService.IssuedToken issued = tokenService.issue("intent_api_expired", Instant.now().minusSeconds(10));

        mockMvc.perform(post("/api/intent/suggest")
                        .header("Authorization", "Bearer " + issued.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"tennis\",\"partySize\":1}"))
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
