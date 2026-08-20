package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementFieldMapper;
import com.example.hackathoncodaro2026.dto.ArrangementPreview;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.PriceQuote;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Address;
import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.model.enums.VenueType;
import com.example.hackathoncodaro2026.repository.ArrangementExtraRepository;
import com.example.hackathoncodaro2026.repository.FuneralHomeRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.DateAssignmentService;
import com.example.hackathoncodaro2026.service.OccupancyService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FuneralArrangementTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FuneralHomeRepository funeralHomeRepository;

    @Autowired
    private ServiceVenueRepository serviceVenueRepository;

    @Autowired
    private ArrangementExtraRepository arrangementExtraRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private DateAssignmentService dateAssignmentService;

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedCreatesWarsawHomesAndNoCoach() {
        assertThat(funeralHomeRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(serviceVenueRepository.count()).isGreaterThanOrEqualTo(10);
        assertThat(arrangementExtraRepository.count()).isGreaterThanOrEqualTo(8);
        assertThat(userRepository.findByRoleOrderByFullNameAsc(Role.MANAGER)).isNotEmpty();
        assertThat(funeralHomeRepository.findByEnabledTrueOrderByNameAsc())
                .allMatch(home -> home.getAddress() != null && "Warszawa".equals(home.getAddress().getCity()));
    }

    @Test
    void chooseStartIsTheEarliestFreeStartAndIsStable() {
        ServiceVenue venue = chapel();
        List<LocalDateTime> starts = dateAssignmentService.availableStarts(venue, FuneralPackage.CLASSIC);
        assertThat(starts).isNotEmpty();
        LocalDateTime first = dateAssignmentService.chooseStart(venue, FuneralPackage.CLASSIC);
        assertThat(first).isEqualTo(starts.getFirst());
        assertThat(dateAssignmentService.chooseStart(venue, FuneralPackage.CLASSIC)).isEqualTo(first);
    }

    @Test
    void deceasedDeathBeforeBirthIsRejected() {
        User user = family("val_dates");
        ArrangementRequest request = validRequest(chapel(), user);
        request.setDateOfBirth(LocalDate.of(2020, 1, 2));
        request.setDateOfDeath(LocalDate.of(2020, 1, 1));
        assertThatThrownBy(() -> reservationService.preview(user, request))
                .isInstanceOf(ReservationException.class);
    }

    @Test
    void attendeesOverCapacityAreRejected() {
        User user = family("val_heads");
        ServiceVenue venue = chapel();
        ArrangementRequest request = validRequest(venue, user);
        request.setAttendees(venue.getMaxAttendees() + 1);
        assertThatThrownBy(() -> reservationService.create(user, request))
                .isInstanceOf(ReservationException.class);
    }

    @Test
    void extrasPricingIsExactAndIndependentOfDate() {
        List<ArrangementExtra> extras = List.of(
                extra("Floral arrangement"),
                extra("Memorial cards")
        );
        BigDecimal first = pricingService.quote(FuneralPackage.ESSENTIAL, extras, 5);
        BigDecimal second = pricingService.quote(FuneralPackage.ESSENTIAL, extras, 5);
        assertThat(first).isEqualByComparingTo(second);
        assertThat(first).isEqualByComparingTo(new BigDecimal("2910.00"));
        User user = family("price_user");
        ArrangementRequest request = validRequest(chapel(), user);
        request.setAttendees(5);
        request.setExtraIds(extras.stream().map(ArrangementExtra::getId).collect(Collectors.toList()));
        PriceQuote quote = reservationService.quote(user, request);
        Reservation saved = reservationService.create(user, request);
        assertThat(quote.getAmount()).isEqualByComparingTo(saved.getTotalAmount());
        assertThat(quote.getFormattedAmount()).isEqualTo(saved.getFormattedTotalAmount());
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2910.00"));
    }

    @Test
    void venueAndAssistantQuotesAndArrangementAuditWork() throws Exception {
        User user = family("quote_channels");
        ServiceVenue venue = chapel();
        ArrangementExtra floral = extra("Floral arrangement");
        ArrangementRequest request = validRequest(venue, user);
        request.setAttendees(5);
        request.setExtraIds(List.of(floral.getId()));
        PriceQuote expected = reservationService.quote(user, request);
        mockMvc.perform(post("/venues/{id}/quote", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", request.getServiceType().name())
                        .param("funeralPackage", request.getFuneralPackage().name())
                        .param("deceasedFullName", request.getDeceasedFullName())
                        .param("dateOfDeath", "2024-03-03")
                        .param("attendees", "5")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .param("extraIds", String.valueOf(floral.getId()))
                        .with(csrf())
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.formattedAmount").value(expected.getFormattedAmount()));
        mockMvc.perform(get("/api/reservation-assistant/venues/{id}/extras", venue.getId())
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Floral arrangement')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name=='Urn selection')]").isEmpty());
        String quoteJson = "{"
                + "\"venueId\":" + venue.getId() + ","
                + "\"serviceType\":\"" + request.getServiceType().name() + "\","
                + "\"funeralPackage\":\"ESSENTIAL\","
                + "\"deceasedFullName\":\"Remembered Person\","
                + "\"dateOfDeath\":\"2024-03-03\","
                + "\"attendees\":5,"
                + "\"paymentMethod\":\"CASH\","
                + "\"extraIds\":[" + floral.getId() + "]"
                + "}";
        mockMvc.perform(post("/api/reservation-assistant/quote")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(quoteJson)
                        .with(csrf())
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedAmount").value(expected.getFormattedAmount()));
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/reservation-assistant/arrangements")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(quoteJson)
                            .with(csrf())
                            .with(user(user.getUsername()).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formattedAmount").value(expected.getFormattedAmount()));
            String joined = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining(" "));
            assertThat(joined).contains("ASSISTANT_RESERVATION_SUCCESS");
            assertThat(joined).contains("source=CHAT_ASSISTANT");
            assertThat(joined).doesNotContain("Remembered Person");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void urnExtraAppliesOnlyToCremation() {
        ArrangementExtra urn = extra("Urn selection");
        User user = family("urn_user");
        ServiceVenue chapel = chapel();
        ArrangementRequest burial = validRequest(chapel, user);
        burial.setServiceType(ServiceType.BURIAL_CEREMONY);
        burial.setExtraIds(List.of(urn.getId()));
        Reservation saved = reservationService.create(user, burial);
        assertThat(saved.getExtrasSummary()).doesNotContain("Urn");
    }

    @Test
    void previewDatesAreCurrentlyAvailable() {
        User user = family("preview_user");
        ServiceVenue venue = chapel();
        ArrangementPreview preview = reservationService.preview(user, validRequest(venue, user));
        assertThat(preview.dates()).isNotEmpty();
        for (LocalDate date : preview.dates()) {
            assertThat(dateAssignmentService.availableStarts(venue, FuneralPackage.ESSENTIAL))
                    .anyMatch(start -> start.toLocalDate().equals(date));
        }
    }

    @Test
    void familySpinIsPendingAndOccupiesBoard() {
        User user = family("spin_user");
        ServiceVenue venue = chapel();
        Reservation saved = reservationService.create(user, validRequest(venue, user));
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getStartAt()).isNotNull();
        assertThat(occupancyService.gridFor(saved.getStartAt().toLocalDate(), venue.getFuneralHome().getId()).getRows())
                .anyMatch(row -> row.getVenueId().equals(venue.getId())
                        && row.getCells().stream().anyMatch(cell -> "pending".equals(cell.getLevel())));
        assertThat(reservationService.findManagerQueue(saved.getStartAt().toLocalDate()))
                .extracting(Reservation::getId)
                .contains(saved.getId());
    }

    @Test
    void managerSpinIsConfirmed() {
        User manager = userService.findByUsername("manager").orElseThrow();
        Reservation saved = reservationService.create(manager, validRequest(chapel(), manager));
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pendingEditCannotChangeVenueOrPackageOrDate() {
        User user = family("edit_user");
        Reservation saved = reservationService.create(user, validRequest(chapel(), user));
        LocalDateTime start = saved.getStartAt();
        ArrangementRequest update = validRequest(saved.getVenue(), user);
        update.setDeceasedFullName("Updated Memorial Name");
        update.setAttendees(3);
        update.setFuneralPackage(FuneralPackage.TRIBUTE);
        assertThatThrownBy(() -> reservationService.update(user, saved.getId(), update))
                .isInstanceOf(ReservationException.class);
        update.setFuneralPackage(saved.getFuneralPackage());
        reservationService.update(user, saved.getId(), update);
        Reservation reloaded = reservationRepository.findWithDetailsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStartAt()).isEqualTo(start);
        assertThat(reloaded.getDeceasedFullName()).isEqualTo("Updated Memorial Name");
        assertThat(reloaded.getAttendees()).isEqualTo(3);
    }

    @Test
    void confirmedCannotBeCancelled() {
        User manager = userService.findByUsername("manager").orElseThrow();
        Reservation saved = reservationService.create(manager, validRequest(chapel(), manager));
        assertThatThrownBy(() -> reservationService.cancel(manager, saved.getId()))
                .isInstanceOf(ReservationException.class);
    }

    @Test
    void cancelledFreesSlotForAnotherFamily() {
        User first = family("free_one");
        User second = family("free_two");
        ServiceVenue venue = chapel();
        Reservation held = reservationService.create(first, validRequest(venue, first));
        reservationService.cancel(first, held.getId());
        Reservation next = reservationService.create(second, validRequest(venue, second));
        assertThat(next.getId()).isNotEqualTo(held.getId());
        assertThat(next.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void adjacentSlotsDoNotOverlap() {
        User user = family("adj_user");
        ServiceVenue venue = chapel();
        Reservation saved = reservationService.create(user, validRequest(venue, user));
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt().plusMinutes(FuneralPackage.ESSENTIAL.getDurationMinutes()));
        assertThat(reservationRepository.countOverlapping(
                venue.getId(),
                ReservationStatus.occupying(),
                saved.getEndAt(),
                saved.getEndAt().plusMinutes(FuneralPackage.ESSENTIAL.getDurationMinutes())
        )).isZero();
    }

    @Test
    void logsOmitSensitiveDeceasedAndContactValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            User user = family("log_user");
            ArrangementRequest request = validRequest(chapel(), user);
            request.setDeceasedFullName("UNIQUE_DECEASED_ZXCVBNM");
            request.setNote("UNIQUE_FAMILY_NOTE_QWERTY");
            request.setPhone("+48 555 019 876");
            reservationService.create(user, request);
            String joined = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining(" "));
            assertThat(joined).doesNotContain("UNIQUE_DECEASED_ZXCVBNM");
            assertThat(joined).doesNotContain("UNIQUE_FAMILY_NOTE_QWERTY");
            assertThat(joined).doesNotContain("555 019 876");
            assertThat(joined).doesNotContain("log_user@example.com");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void arrangementRequestHasNoDateTimeChoiceFields() {
        assertThat(ArrangementRequest.class.getDeclaredFields())
                .extracting(field -> field.getName())
                .doesNotContain("date", "startTime", "startAt", "endAt", "durationHours");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void spinIgnoresClientDateParameters() throws Exception {
        ServiceVenue venue = chapel();
        String body = mockMvc.perform(post("/venues/{id}/spin", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .param("date", "1999-01-01")
                        .param("startTime", "00:00")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("1999-01-01");
    }

    @Test
    void idorPreventsEditingSomeoneElsesArrangement() {
        User owner = family("idor_owner");
        User other = family("idor_other");
        Reservation saved = reservationService.create(owner, validRequest(chapel(), owner));
        ArrangementRequest update = validRequest(saved.getVenue(), other);
        assertThatThrownBy(() -> reservationService.update(other, saved.getId(), update))
                .isInstanceOf(ReservationException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void managerCanConfirmPendingFamilyArrangement() throws Exception {
        User owner = family("queue_owner");
        Reservation saved = reservationService.create(owner, validRequest(chapel(), owner));
        mockMvc.perform(post("/manager/reservations/{id}/confirm", saved.getId())
                        .param("date", saved.getStartAt().toLocalDate().toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reservationRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void activeRoutesSmoke() throws Exception {
        ServiceVenue venue = chapel();
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("EverRest")));
        mockMvc.perform(get("/homes")).andExpect(status().isOk());
        mockMvc.perform(get("/homes/{id}", venue.getFuneralHome().getId())).andExpect(status().isOk());
        mockMvc.perform(get("/venues/{id}", venue.getId())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"date\""))))
                .andExpect(content().string(containsString("Confirm arrangements")))
                .andExpect(content().string(containsString("Congratulations, but we are very sorry!")))
                .andExpect(content().string(containsString("id=\"wheel-overlay\"")))
                .andExpect(content().string(containsString("aria-modal=\"true\"")))
                .andExpect(content().string(not(containsString("Spin for a date"))));
        mockMvc.perform(get("/reservations")).andExpect(status().isOk());
        mockMvc.perform(get("/availability")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Availability")))
                .andExpect(content().string(containsString("Start arrangements")))
                .andExpect(content().string(containsString("occ-arrange")))
                .andExpect(content().string(containsString("href=\"/venues/")))
                .andExpect(content().string(not(containsString("occ-arrange\" href=\"/venues/1?date"))));
        mockMvc.perform(get("/occupancy")).andExpect(status().isOk());
        mockMvc.perform(get("/profile")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("type=\"file\""))));
        mockMvc.perform(get("/notifications")).andExpect(status().isOk());
        mockMvc.perform(get("/manager/reservations")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/users")).andExpect(status().isOk());
    }

    @Test
    void assistantSourceDoesNotPersistSensitiveFields() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/assistant.js"));
        int start = js.indexOf("function persistSafe");
        int end = js.indexOf("function restoreSafe");
        assertThat(start).isGreaterThan(0);
        String persist = js.substring(start, end);
        assertThat(persist).doesNotContain("deceasedFullName");
        assertThat(persist).doesNotContain("dateOfBirth");
        assertThat(persist).doesNotContain("dateOfDeath");
        assertThat(persist).doesNotContain("draft.phone");
        assertThat(persist).doesNotContain("draft.note");
        assertThat(persist).doesNotContain("email");
    }

    @Test
    void loginAndRegisterHideEvelynAndKeepInk() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(count(html, "id=\"everrest-assistant\"")).isZero();
        assertThat(count(html, "id=\"ca-launcher\"")).isZero();
        assertThat(count(html, "id=\"ca-panel\"")).isZero();
        assertThat(html).doesNotContain("Ask Evelyn");
        assertThat(html).doesNotContain("id=\"ca-close\"");
        assertThat(html).contains("class=\"ink-veil\"");
        assertThat(html).contains("class=\"ink-shapes\"");
        assertThat(html).contains("class=\"ink-blot");
        assertThat(count(html, "fragments/assistant")).isZero();
        String register = mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(count(register, "id=\"everrest-assistant\"")).isZero();
        assertThat(register).doesNotContain("Ask Evelyn");
        assertThat(register).contains("class=\"ink-veil\"");
        assertThat(register).doesNotContain("id=\"ca-close\"");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void representativePagesContainOneEvelynWidget() throws Exception {
        ServiceVenue venue = chapel();
        for (String path : List.of("/", "/reservations", "/availability", "/notifications")) {
            String html = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(count(html, "id=\"everrest-assistant\"")).as(path).isEqualTo(1);
            assertThat(count(html, "id=\"ca-launcher\"")).as(path).isEqualTo(1);
            assertThat(count(html, "id=\"ca-panel\"")).as(path).isEqualTo(1);
            assertThat(html).as(path).contains("id=\"ca-panel\" hidden");
            assertThat(html).as(path).contains("aria-expanded=\"false\"");
            assertThat(html).as(path).doesNotContain("class=\"ca-root is-open\"");
            assertThat(html).as(path).doesNotContain("id=\"ca-close\"");
        }
        String arrange = mockMvc.perform(get("/venues/{id}", venue.getId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(count(arrange, "id=\"everrest-assistant\"")).isEqualTo(1);
        assertThat(count(arrange, "id=\"wheel-overlay\"")).isEqualTo(1);
    }

    @Test
    void evelynToggleRestartAndClosedDefaultAreInSource() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/assistant.js"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        String widget = Files.readString(Path.of("src/main/resources/templates/fragments/assistant.html"));
        assertThat(widget).doesNotContain("id=\"ca-close\"");
        assertThat(widget).contains("id=\"ca-restart\"");
        assertThat(widget).contains("aria-expanded=\"false\"");
        assertThat(widget).contains("id=\"ca-panel\" hidden");
        assertThat(js).doesNotContain("closeBtn");
        assertThat(js).doesNotContain("getElementById(\"ca-close\")");
        assertThat(js).contains("var SCHEMA = 3");
        assertThat(js).contains("everrest-evelyn-v3:");
        assertThat(js).contains("open: false");
        assertThat(js).contains("function startClosed");
        assertThat(js).contains("startClosed();");
        assertThat(js).doesNotContain("if (loadStore().open === true)");
        assertThat(js).contains("removeProperty(\"left\")");
        assertThat(js).contains("removeProperty(\"top\")");
        assertThat(js).contains("removeProperty(\"transform\")");
        assertThat(js).contains("currentStep: step");
        assertThat(js).contains("data-initialized");
        assertThat(js).contains("window.__everrestEvelynInit");
        assertThat(js).doesNotContain("root.style.left = stored");
        assertThat(js).doesNotContain("clientX");
        int persistStart = js.indexOf("function persistSafe");
        int persistEnd = js.indexOf("function restoreSafe");
        String persist = js.substring(persistStart, persistEnd);
        assertThat(persist).contains("currentStep: step");
        assertThat(persist).contains("open: !panel.hidden");
        assertThat(persist).doesNotContain("deceasedFullName");
        int closeStart = js.indexOf("function closePanel");
        int closeEnd = js.indexOf("launcher.addEventListener");
        String close = js.substring(closeStart, closeEnd);
        assertThat(close).doesNotContain("emptyDraft");
        assertThat(close).doesNotContain("draft =");
        int restartStart = js.indexOf("function restart");
        int restartEnd = js.indexOf("function openPanel");
        String restart = js.substring(restartStart, restartEnd);
        assertThat(restart).contains("emptyDraft()");
        assertThat(restart).doesNotContain("closePanel");
        assertThat(restart).doesNotContain("location.reload");
        assertThat(restart).doesNotContain("logout");
        assertThat(restart).doesNotContain("localStorage.clear");
        assertThat(css).contains("left: auto !important");
        assertThat(css).contains("top: auto !important");
        assertThat(css).contains("right: 1.1rem !important");
        assertThat(css).contains("bottom: 1.1rem !important");
        assertThat(css).contains(".ca-panel[hidden]");
        assertThat(css).contains("flex: 1 1 0");
        assertThat(css).contains("min-height: 0");
        assertThat(css).contains("max-height: 64%");
        assertThat(css).doesNotContain("max-height: 14rem");
        assertThat(js).contains("er-text-area");
        assertThat(js).contains("placeholder = \"Private family note\"");
        assertThat(css).contains("display: none !important");
        assertThat(css).contains(".ink-veil");
        assertThat(css).contains(".ink-blot");
        assertThat(css).contains("@keyframes ink-drift");
        assertThat(css).contains(".ink-floater");
        assertThat(css).contains("@keyframes ink-cloud");
        assertThat(css).contains("pointer-events: none");
        int ink = css.indexOf(".ink-veil {");
        assertThat(ink).isGreaterThan(0);
        assertThat(css.substring(ink, ink + 180)).contains("pointer-events: none");
        assertThat(css).contains("@media (prefers-reduced-motion: reduce)");
        int reduced = css.lastIndexOf("@media (prefers-reduced-motion: reduce)");
        assertThat(css.substring(reduced)).contains(".ink-veil::before");
        assertThat(css.substring(reduced)).contains("animation: none");
    }

    @Test
    void historyCardImageThenBodyAndBrandDataAreFuneralThemed() throws Exception {
        String history = Files.readString(Path.of("src/main/resources/templates/reservations/history.html"));
        String occupancy = Files.readString(Path.of("src/main/resources/templates/occupancy/index.html"));
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        String readme = Files.readString(Path.of("README.md"));
        String seed = Files.readString(Path.of("src/main/java/com/example/hackathoncodaro2026/config/DataInitializer.java"));
        String launcher = Files.readString(Path.of("src/main/java/com/example/hackathoncodaro2026/config/BrowserLauncher.java"));
        String mark = Files.readString(Path.of("src/main/resources/static/images/brand/mark.svg"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        assertThat(history).contains("history-card-image");
        assertThat(history).contains("history-card-body");
        assertThat(history).contains("history-card-actions");
        assertThat(history.indexOf("history-card-image")).isLessThan(history.indexOf("history-card-body"));
        assertThat(css).contains("flex-direction: column");
        assertThat(occupancy).contains("Start arrangements");
        assertThat(occupancy).contains("@{/venues/{id}(id=${row.venueId})}");
        assertThat(occupancy).doesNotContain("name=\"startTime\"");
        assertThat(yml).contains("name: everrest-funeral-arrangements");
        assertThat(yml).contains("jdbc:h2:file:./data/everrest;LOCK_TIMEOUT=5000");
        assertThat(yml).doesNotContain("sportsbooking");
        assertThat(yml).contains("open-h2-console: true");
        assertThat(launcher).contains("/h2-launch");
        assertThat(readme).contains("jdbc:h2:file:./data/everrest;LOCK_TIMEOUT=5000");
        assertThat(readme).contains("vendor H2 logo");
        assertThat(readme).contains("/h2-launch");
        assertThat(readme).doesNotContain("sportsbooking");
        assertThat(seed.toLowerCase()).doesNotContain("tennis");
        assertThat(seed.toLowerCase()).doesNotContain("basketball");
        assertThat(seed.toLowerCase()).doesNotContain("coach");
        assertThat(seed).doesNotContain("sportsbooking");
        assertThat(mark).contains("M22 18 H46");
        assertThat(css).contains("wheel-overlay-copy");
        assertThat(css).contains("wheel-peg-ring");
        assertThat(css).contains("wheel-hub");
        assertThat(css).contains("text-align: center");
    }

    @Test
    void manualConfirmPostsSpinWithoutSeparateSpinControl() throws Exception {
        String arrangeJs = Files.readString(Path.of("src/main/resources/static/js/arrange.js"));
        String wheelJs = Files.readString(Path.of("src/main/resources/static/js/wheel.js"));
        String assistantJs = Files.readString(Path.of("src/main/resources/static/js/assistant.js"));
        assertThat(arrangeJs).contains("data-spin-url");
        assertThat(arrangeJs).contains("EverRestWheel.open");
        assertThat(arrangeJs.indexOf("post(previewUrl")).isLessThan(arrangeJs.indexOf("EverRestWheel.open"));
        assertThat(arrangeJs).doesNotContain("getElementById(\"spin-btn\")");
        assertThat(arrangeJs).doesNotContain("getElementById(\"preview-btn\")");
        assertThat(wheelJs).doesNotContain("Math.random");
        assertThat(wheelJs).contains("SPIN_SECONDS = 5.8");
        assertThat(wheelJs).contains("SPIN_DELAY_MS = 6000");
        assertThat(wheelJs).contains("translate(-50%, -50%)");
        assertThat(wheelJs).contains("cubic-bezier(0.22, 0.61, 0.12, 1)");
        String h2Launch = Files.readString(Path.of("src/main/resources/templates/h2-launch.html"));
        assertThat(h2Launch).contains("/images/brand/mark.svg");
        assertThat(assistantJs).contains("Confirm arrangements");
        assertThat(assistantJs).doesNotContain("Spin for a date");
        assertThat(assistantJs.indexOf("api(\"/preview\"")).isLessThan(assistantJs.indexOf("EverRestWheel.open"));
        String calendarJs = Files.readString(Path.of("src/main/resources/static/js/calendar.js"));
        String nav = Files.readString(Path.of("src/main/resources/templates/fragments/nav.html"));
        assertThat(calendarJs).contains("EverRestCalendar");
        assertThat(nav).contains("/js/calendar.js");
        String html = Files.readString(Path.of("src/main/resources/templates/venues/arrange.html"));
        assertThat(html).contains("Confirm arrangements");
        assertThat(html).contains("type=\"date\"");
        assertThat(html).contains("data-max-today");
        assertThat(html).contains("type=\"text\"");
        assertThat(html).contains("holds up to");
        assertThat(html).doesNotContain("id=\"attendees\" th:field=\"*{attendees}\" type=\"number\"");
        assertThat(html).doesNotContain("id=\"spin-btn\"");
        assertThat(html).doesNotContain("id=\"preview-btn\"");
        String fieldsJs = Files.readString(Path.of("src/main/resources/static/js/fields.js"));
        assertThat(fieldsJs).contains("EverRestFields");
        assertThat(fieldsJs).contains("er-text-guests");
        assertThat(fieldsJs).contains("replace(/[^0-9]/g, \"\")");
        assertThat(nav).contains("/js/fields.js");
        assertThat(arrangeJs).contains("parseGuests");
        assertThat(assistantJs).contains("data-guest-field");
        assertThat(assistantJs).contains("holds up to");
        assertThat(assistantJs).contains("EverRestFields");
        String css = Files.readString(Path.of("src/main/resources/static/css/app.css"));
        assertThat(css).contains(".er-text");
        assertThat(css).contains(".ca-controls .er-text");
        assertThat(css).contains("#c4a574");
        assertThat(css).contains("textarea.er-text");
        assertThat(assistantJs).contains("er-text-area");
        assertThat(arrangeJs).contains("data-quote-url");
        assertThat(arrangeJs).contains("refreshQuote");
        assertThat(arrangeJs).contains("showQuotedTotal");
        assertThat(html).contains("data-quote-url");
        assertThat(html).contains("quote-amount");
        assertThat(assistantJs).contains("ca-selected-extras");
        assertThat(assistantJs).contains("fillExtras");
        assertThat(assistantJs).contains("api(\"/quote\"");
        assertThat(assistantJs).contains("Quoted total");
        assertThat(assistantJs).doesNotContain("draft.extraIds.splice");
        assertThat(css).contains(".ca-selected-extras");
        String editHtml = Files.readString(Path.of("src/main/resources/templates/reservations/edit.html"));
        assertThat(editHtml).contains("holds up to");
        assertThat(editHtml).contains("er-text-area");
        assertThat(editHtml).doesNotContain("id=\"attendees\" th:field=\"*{attendees}\" type=\"number\"");
        assertThat(html).contains("er-text-area");
    }

    @Test
    void venuesHaveDistinctGuestCapacitiesAndAttendeesRoundTrip() {
        List<ServiceVenue> venues = serviceVenueRepository.findByEnabledTrueOrderByNameAsc();
        assertThat(venues).hasSizeGreaterThan(3);
        assertThat(venues.stream().map(ServiceVenue::getMaxAttendees).collect(Collectors.toSet()))
                .hasSizeGreaterThan(3);
        ServiceVenue venue = chapel();
        User user = family("guest_roundtrip");
        ArrangementRequest request = validRequest(venue, user);
        request.setAttendees(Math.min(12, venue.getMaxAttendees()));
        Reservation saved = reservationService.create(user, request);
        Reservation loaded = reservationRepository.findWithDetailsById(saved.getId()).orElseThrow();
        assertThat(loaded.getAttendees()).isEqualTo(request.getAttendees());
        assertThat(loaded.getDeceasedFullName()).isEqualTo("Remembered Person");
        assertThat(loaded.getStartAt()).isNotNull();
        assertThat(reservationService.findForUser(user)).extracting(Reservation::getId).contains(saved.getId());
    }

    @Test
    void fieldMapperCoversRequiredGroups() {
        assertThat(ArrangementFieldMapper.stepFor("venueId")).isEqualTo("venue");
        assertThat(ArrangementFieldMapper.stepFor("serviceType")).isEqualTo("service");
        assertThat(ArrangementFieldMapper.stepFor("funeralPackage")).isEqualTo("pack");
        assertThat(ArrangementFieldMapper.stepFor("deceasedFullName")).isEqualTo("deceased");
        assertThat(ArrangementFieldMapper.stepFor("dateOfDeath")).isEqualTo("deceased");
        assertThat(ArrangementFieldMapper.stepFor("attendees")).isEqualTo("attendees");
        assertThat(ArrangementFieldMapper.stepFor("phone")).isEqualTo("phone");
        assertThat(ArrangementFieldMapper.stepFor("paymentMethod")).isEqualTo("payment");
        assertThat(ArrangementFieldMapper.stepFor("extraIds")).isEqualTo("extras");
    }

    @Test
    void missingPhoneMapsToPhoneFieldAndStep() {
        User user = familyWithoutPhone("nophone_book");
        ArrangementRequest request = validRequest(chapel(), user);
        request.setPhone(null);
        assertThatThrownBy(() -> reservationService.create(user, request))
                .isInstanceOf(ReservationException.class)
                .satisfies(error -> {
                    ReservationException ex = (ReservationException) error;
                    assertThat(ex.getField()).isEqualTo("phone");
                    assertThat(ex.getCode()).isEqualTo("PHONE_REQUIRED");
                    assertThat(ArrangementFieldMapper.stepFor(ex.getField())).isEqualTo("phone");
                });
        assertThat(reservationRepository.findByUserIdWithDetails(user.getId())).isEmpty();
    }

    @Test
    void invalidAttendeesMapsToAttendeesFieldAndStep() {
        User user = family("bad_heads");
        ServiceVenue venue = chapel();
        ArrangementRequest request = validRequest(venue, user);
        request.setAttendees(venue.getMaxAttendees() + 8);
        assertThatThrownBy(() -> reservationService.create(user, request))
                .isInstanceOf(ReservationException.class)
                .satisfies(error -> {
                    ReservationException ex = (ReservationException) error;
                    assertThat(ex.getField()).isEqualTo("attendees");
                    assertThat(ArrangementFieldMapper.stepFor(ex.getField())).isEqualTo("attendees");
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void missingPhoneJsonIncludesFieldAndStep() throws Exception {
        User user = familyWithoutPhone("json_nophone");
        ServiceVenue venue = chapel();
        mockMvc.perform(post("/venues/{id}/spin", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .with(csrf())
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("phone"))
                .andExpect(jsonPath("$.step").value("phone"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidAttendeesJsonIncludesFieldAndStep() throws Exception {
        ServiceVenue venue = chapel();
        mockMvc.perform(post("/venues/{id}/spin", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", String.valueOf(venue.getMaxAttendees() + 4))
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("attendees"))
                .andExpect(jsonPath("$.step").value("attendees"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidDeathDateOnPreviewIncludesFieldAndStep() throws Exception {
        ServiceVenue venue = chapel();
        mockMvc.perform(post("/venues/{id}/preview", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfBirth", "2024-06-01")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("dateOfDeath"))
                .andExpect(jsonPath("$.step").value("deceased"));
        mockMvc.perform(post("/venues/{id}/preview", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2099-01-01")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("dateOfDeath"))
                .andExpect(jsonPath("$.step").value("deceased"));
    }

    @Test
    void repeatedSubmissionTokenReturnsSameReservation() {
        User user = family("idem_user");
        ServiceVenue venue = chapel();
        ArrangementRequest request = validRequest(venue, user);
        String token = UUID.randomUUID().toString();
        request.setSubmissionToken(token);
        Reservation first = reservationService.create(user, request);
        Reservation second = reservationService.create(user, request);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(reservationRepository.findByUserIdWithDetails(user.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void doubleSpinPostWithTokenDoesNotCreateSecondRow() throws Exception {
        ServiceVenue venue = chapel();
        String token = UUID.randomUUID().toString();
        String first = mockMvc.perform(post("/venues/{id}/spin", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .param("submissionToken", token)
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.startAt").exists())
                .andExpect(jsonPath("$.dates").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = mockMvc.perform(post("/venues/{id}/spin", venue.getId())
                        .param("venueId", String.valueOf(venue.getId()))
                        .param("serviceType", ServiceType.MEMORIAL_SERVICE.name())
                        .param("funeralPackage", FuneralPackage.ESSENTIAL.name())
                        .param("deceasedFullName", "Memorial Name")
                        .param("dateOfDeath", "2024-02-02")
                        .param("attendees", "2")
                        .param("paymentMethod", PaymentMethod.CASH.name())
                        .param("submissionToken", token)
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(second).contains("\"id\":");
        assertThat(extractJsonId(first)).isEqualTo(extractJsonId(second));
    }

    @Test
    void spinPayloadUsesServerWinnerDate() {
        User user = family("winner_user");
        ArrangementCreateResponse response = reservationService.spin(user, validRequest(chapel(), user));
        assertThat(response.startAt()).isNotNull();
        assertThat(response.dates()).contains(response.startAt().toLocalDate());
        assertThat(response.id()).isEqualTo(reservationRepository.findById(response.id()).orElseThrow().getId());
        assertThat(reservationRepository.findById(response.id()).orElseThrow().getStartAt()).isEqualTo(response.startAt());
    }

    @Test
    void staleAvailabilityCreatesNoReservation() {
        FuneralHome home = funeralHomeRepository.findByEnabledTrueOrderByNameAsc().getFirst();
        ServiceVenue venue = new ServiceVenue();
        venue.setFuneralHome(home);
        venue.setName("Closed Window Chapel");
        venue.setType(VenueType.CHAPEL);
        venue.setAddress(new Address("Testowa", "9", "00-001", "Śródmieście"));
        venue.setMaxAttendees(12);
        venue.setOpeningTime(LocalTime.of(10, 0));
        venue.setClosingTime(LocalTime.of(11, 30));
        venue.setSlotDurationMinutes(30);
        venue.setEnabled(true);
        ServiceVenue savedVenue = serviceVenueRepository.saveAndFlush(venue);
        List<LocalDateTime> starts = dateAssignmentService.availableStarts(savedVenue, FuneralPackage.ESSENTIAL);
        assertThat(starts).isNotEmpty();
        User filler = family("stale_fill");
        for (LocalDateTime startAt : starts) {
            occupy(savedVenue, filler, startAt);
        }
        long before = reservationRepository.count();
        User extra = family("stale_extra");
        assertThatThrownBy(() -> reservationService.create(extra, validRequest(savedVenue, extra)))
                .isInstanceOf(ReservationException.class)
                .satisfies(error -> assertThat(((ReservationException) error).getCode()).isIn("NO_SLOTS", "STALE_SLOT"));
        assertThat(reservationRepository.count()).isEqualTo(before);
    }

    @Test
    void downloadedImagesAreValidJpegOrSvg() throws Exception {
        Path root = Path.of("src/main/resources/static/images");
        assertThat(Files.exists(root)).isTrue();
        try (var stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            assertThat(files).isNotEmpty();
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase();
                byte[] bytes = Files.readAllBytes(file);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    assertThat(bytes.length).isGreaterThan(2000);
                    assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
                    assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);
                } else if (name.endsWith(".svg")) {
                    assertThat(new String(bytes)).contains("<svg");
                }
            }
        }
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
                .filter(venue -> venue.getType() == VenueType.CHAPEL && !venue.getName().startsWith("Single Opening"))
                .findFirst()
                .orElseThrow();
    }

    private ArrangementRequest validRequest(ServiceVenue venue, User user) {
        ArrangementRequest request = new ArrangementRequest();
        request.setVenueId(venue.getId());
        request.setServiceType(ServiceType.MEMORIAL_SERVICE);
        if (!ServiceType.MEMORIAL_SERVICE.allows(venue.getType())) {
            request.setServiceType(ServiceType.BURIAL_CEREMONY);
        }
        request.setFuneralPackage(FuneralPackage.ESSENTIAL);
        request.setDeceasedFullName("Remembered Person");
        request.setDateOfDeath(LocalDate.of(2024, 3, 3));
        request.setAttendees(2);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPhone(user.getPhone());
        return request;
    }

    private User familyWithoutPhone(String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        }
        RegistrationRequest registration = new RegistrationRequest();
        registration.setUsername(username);
        registration.setEmail(username + "@example.com");
        registration.setFullName(username);
        registration.setPassword("Password1");
        registration.setConfirmPassword("Password1");
        return userService.register(registration);
    }

    private void occupy(ServiceVenue venue, User user, LocalDateTime startAt) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setVenue(venue);
        reservation.setServiceType(ServiceType.MEMORIAL_SERVICE);
        reservation.setFuneralPackage(FuneralPackage.ESSENTIAL);
        reservation.setDeceasedFullName("Held Slot");
        reservation.setDateOfDeath(LocalDate.of(2024, 1, 1));
        reservation.setAttendees(1);
        reservation.setStartAt(startAt);
        reservation.setEndAt(startAt.plusMinutes(FuneralPackage.ESSENTIAL.getDurationMinutes()));
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPaymentMethod(PaymentMethod.CASH);
        reservation.setTotalAmount(FuneralPackage.ESSENTIAL.getBasePrice());
        reservationRepository.saveAndFlush(reservation);
    }

    private int count(String haystack, String needle) {
        int total = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return total;
            }
            total++;
            from = at + needle.length();
        }
    }

    private Long extractJsonId(String body) {
        int start = body.indexOf("\"id\":");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int cursor = start + 5;
        while (cursor < body.length() && (body.charAt(cursor) == ' ')) {
            cursor++;
        }
        int end = cursor;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.valueOf(body.substring(cursor, end));
    }

    private ArrangementExtra extra(String name) {
        return arrangementExtraRepository.findByEnabledTrueOrderByNameAsc().stream()
                .filter(item -> name.equalsIgnoreCase(item.getName()))
                .findFirst()
                .orElseThrow();
    }
}
