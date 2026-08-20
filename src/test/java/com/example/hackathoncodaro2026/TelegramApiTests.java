package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.model.enums.VenueType;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.UserService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelegramApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceVenueRepository serviceVenueRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void tokenRejectsBadPassword() throws Exception {
        mockMvc.perform(post("/api/telegram/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenHistoryVenuesAndRandomAssignmentPersist() throws Exception {
        User user = family("tg_family");
        ServiceVenue venue = chapel();
        String tokenJson = mockMvc.perform(post("/api/telegram/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"Password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.phoneRequired").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(tokenJson, "$.token");
        mockMvc.perform(get("/api/telegram/venues")
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("attendees", "12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maxAttendees").isNumber())
                .andExpect(jsonPath("$[0].name").isNotEmpty());
        String venuesJson = mockMvc.perform(get("/api/telegram/venues")
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("attendees", "12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> capacities = JsonPath.read(venuesJson, "$[*].maxAttendees");
        assertThat(capacities).hasSizeGreaterThan(1);
        assertThat(capacities.stream().distinct().collect(Collectors.toList())).hasSizeGreaterThan(1);
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String createdJson = mockMvc.perform(post("/api/telegram/arrangements")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{"
                                    + "\"venueId\":" + venue.getId() + ","
                                    + "\"serviceType\":\"MEMORIAL_SERVICE\","
                                    + "\"deceasedFullName\":\"Telegram Remembered\","
                                    + "\"dateOfDeath\":\"2024-02-02\","
                                    + "\"attendees\":12,"
                                    + "\"start\":\"1999-01-01T00:00:00\","
                                    + "\"end\":\"1999-01-01T01:00:00\""
                                    + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.startAt").exists())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String startAt = JsonPath.read(createdJson, "$.startAt");
            assertThat(startAt).isNotBlank();
            assertThat(startAt).doesNotContain("1999-01-01");
            Integer id = JsonPath.read(createdJson, "$.id");
            assertThat(reservationRepository.findWithDetailsById(id.longValue()).orElseThrow().getAttendees()).isEqualTo(12);
            mockMvc.perform(get("/api/telegram/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].reservationId").value(id))
                    .andExpect(jsonPath("$[0].attendees").value(12))
                    .andExpect(jsonPath("$[0].venueName").value(venue.getName()));
            String joined = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining(" "));
            assertThat(joined).contains("TELEGRAM_RESERVATION_SUCCESS");
            assertThat(joined).contains("source=TELEGRAM");
            assertThat(joined).doesNotContain("Telegram Remembered");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void historyRequiresToken() throws Exception {
        mockMvc.perform(get("/api/telegram/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void telegramRejectsGuestCountOverVenueCapacity() throws Exception {
        User user = family("tg_overcap");
        ServiceVenue venue = chapel();
        String tokenJson = mockMvc.perform(post("/api/telegram/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"Password1\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(tokenJson, "$.token");
        mockMvc.perform(post("/api/telegram/arrangements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{"
                                + "\"venueId\":" + venue.getId() + ","
                                + "\"serviceType\":\"MEMORIAL_SERVICE\","
                                + "\"deceasedFullName\":\"Over Capacity\","
                                + "\"dateOfDeath\":\"2024-02-02\","
                                + "\"attendees\":" + (venue.getMaxAttendees() + 8)
                                + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("attendees"));
        assertThat(reservationRepository.findByUserIdWithDetails(user.getId())).isEmpty();
    }

    @Test
    void arrangementRequestHasNoChosenSlotFields() {
        assertThat(ArrangementRequest.class.getDeclaredFields())
                .extracting(field -> field.getName())
                .doesNotContain("date", "startTime", "startAt", "endAt", "start", "end");
    }

    private User family(String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        }
        RegistrationRequest registration = new RegistrationRequest();
        registration.setUsername(username);
        registration.setEmail(username + "@example.com");
        registration.setFullName(username);
        registration.setPassword("Password1");
        registration.setConfirmPassword("Password1");
        registration.setPhone("+48 555 010 100");
        return userService.register(registration);
    }

    private ServiceVenue chapel() {
        return serviceVenueRepository.findByEnabledTrueOrderByNameAsc().stream()
                .filter(item -> item.getType() == VenueType.CHAPEL && !item.getName().startsWith("Single Opening"))
                .findFirst()
                .orElseThrow();
    }
}
