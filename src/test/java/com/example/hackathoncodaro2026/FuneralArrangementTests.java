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
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2910.00"));
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
                .andExpect(content().string(containsString("Availability")));
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
    @WithMockUser(username = "admin", roles = "ADMIN")
    void evelynCloseDoesNotClearDraftAndBindsAfterLoad() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/assistant.js"));
        assertThat(js).contains("closeBtn.addEventListener(\"click\", closePanel)");
        assertThat(js).contains("closest(\"button,input,select,textarea,a\")");
        assertThat(js).contains("launcher.setAttribute(\"aria-expanded\", \"false\")");
        assertThat(js).contains("launcher.focus()");
        int closeStart = js.indexOf("function closePanel");
        int closeEnd = js.indexOf("function isInteractive");
        assertThat(closeStart).isGreaterThan(0);
        assertThat(closeEnd).isGreaterThan(closeStart);
        String close = js.substring(closeStart, closeEnd);
        assertThat(close).doesNotContain("emptyDraft");
        assertThat(close).doesNotContain("draft =");
        int persistStart = js.indexOf("function persistSafe");
        int persistEnd = js.indexOf("function restoreSafe");
        String persist = js.substring(persistStart, persistEnd);
        assertThat(persist).contains("open: !panel.hidden");
        assertThat(persist).doesNotContain("deceasedFullName");
        assertThat(persist).doesNotContain("draft.phone");
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"ca-close\"")))
                .andExpect(content().string(containsString("aria-label=\"Close guide\"")))
                .andExpect(content().string(containsString("Congratulations, but we are very sorry!")))
                .andExpect(content().string(containsString("id=\"wheel-overlay\"")))
                .andExpect(content().string(containsString("role=\"dialog\"")))
                .andExpect(content().string(containsString("aria-modal=\"true\"")));
    }

    @Test
    void manualConfirmPostsSpinWithoutSeparateSpinControl() throws Exception {
        String arrangeJs = Files.readString(Path.of("src/main/resources/static/js/arrange.js"));
        String wheelJs = Files.readString(Path.of("src/main/resources/static/js/wheel.js"));
        String assistantJs = Files.readString(Path.of("src/main/resources/static/js/assistant.js"));
        assertThat(arrangeJs).contains("data-spin-url");
        assertThat(arrangeJs).contains("EverRestWheel.open");
        assertThat(arrangeJs).doesNotContain("getElementById(\"spin-btn\")");
        assertThat(arrangeJs).doesNotContain("getElementById(\"preview-btn\")");
        assertThat(wheelJs).doesNotContain("Math.random");
        assertThat(assistantJs).contains("Confirm arrangements");
        assertThat(assistantJs).doesNotContain("Spin for a date");
        String html = Files.readString(Path.of("src/main/resources/templates/venues/arrange.html"));
        assertThat(html).contains("Confirm arrangements");
        assertThat(html).doesNotContain("id=\"spin-btn\"");
        assertThat(html).doesNotContain("id=\"preview-btn\"");
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
