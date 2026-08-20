package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.CoachOfferingRequest;
import com.example.hackathoncodaro2026.dto.CoachPickerCard;
import com.example.hackathoncodaro2026.dto.CoachRatingRequest;
import com.example.hackathoncodaro2026.dto.CoachRatingSummary;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.CoachOffering;
import com.example.hackathoncodaro2026.model.CoachRating;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.CoachOfferingRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.repository.UserSportLevelRepository;
import com.example.hackathoncodaro2026.service.CoachOfferingService;
import com.example.hackathoncodaro2026.service.CoachRatingService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.SportSkillLevelCatalog;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachServiceTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoachOfferingService coachOfferingService;

    @Autowired
    private CoachOfferingRepository coachOfferingRepository;

    @Autowired
    private UserSportLevelRepository userSportLevelRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private SportSkillLevelCatalog sportSkillLevelCatalog;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CoachRatingService coachRatingService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanCreateCoachRole() {
        User coach = createCoach("lane_coach", "lane.coach@example.com");
        assertThat(coach.getRole()).isEqualTo(Role.COACH);
        assertThat(coach.isEnabled()).isTrue();
        assertThat(userRepository.findByUsernameIgnoreCase("lane_coach").orElseThrow().getRole()).isEqualTo(Role.COACH);
    }

    @Test
    void initializerDoesNotSeedCoaches() {
        assertThat(userRepository.existsByRole(Role.COACH)).isFalse();
        assertThat(userRepository.findByRoleOrderByFullNameAsc(Role.COACH)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminStaffCreateFormDoesNotExposePhotoUpload() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("type=\"file\""))))
                .andExpect(content().string(not(containsString("name=\"avatar\""))))
                .andExpect(content().string(not(containsString("multipart/form-data"))))
                .andExpect(content().string(not(containsString("Photo"))));
    }

    @Test
    void coachOfferingStoresSportLevelsAndPrice() {
        User coach = createCoach("price_coach", "price.coach@example.com");
        CoachOfferingRequest request = new CoachOfferingRequest();
        request.setSportType(ResourceType.CHAPEL);
        request.setLevels(Set.of("CATHOLIC", "ORTHODOX"));
        request.setPricePerHour(new BigDecimal("80.00"));
        CoachOffering saved = coachOfferingService.save(coach, request);
        assertThat(saved.getSportType()).isEqualTo(ResourceType.CHAPEL);
        assertThat(saved.getLevels()).containsExactlyInAnyOrder("CATHOLIC", "ORTHODOX");
        assertThat(saved.getPricePerHour()).isEqualByComparingTo("80.00");
        assertThat(coachOfferingRepository.findByCoach_IdAndSportType(coach.getId(), ResourceType.CHAPEL)).isPresent();
    }

    @Test
    void offeringRejectsLevelsThatDoNotBelongToSport() {
        User coach = createCoach("mismatch_levels", "mismatch.levels@example.com");
        CoachOfferingRequest request = new CoachOfferingRequest();
        request.setSportType(ResourceType.CHAPEL);
        request.setLevels(Set.of("DRIVER"));
        request.setPricePerHour(new BigDecimal("80.00"));
        assertThatThrownBy(() -> coachOfferingService.save(coach, request))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("level");
    }

    @Test
    void catalogHidesCoachWhoDoesNotCoverSelectedLevel() {
        User beginnerCoach = createCoach("ntrp_low", "ntrp.low@example.com");
        User advancedCoach = createCoach("ntrp_high", "ntrp.high@example.com");
        saveOffering(beginnerCoach, ResourceType.CHAPEL, Set.of("CATHOLIC", "ORTHODOX"), "70.00");
        saveOffering(advancedCoach, ResourceType.CHAPEL, Set.of("JEWISH", "MUSLIM"), "120.00");
        List<CoachOffering> matches = coachOfferingService.findPublished(ResourceType.CHAPEL, "ORTHODOX");
        assertThat(matches).extracting(item -> item.getCoach().getUsername())
                .contains("ntrp_low")
                .doesNotContain("ntrp_high");
    }

    @Test
    void lessonCannotAttachCoach() {
        User coach = createCoach("lesson_coach", "lesson.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("DRIVER"), "90.00");
        User player = player("lesson_player", "lesson.player@example.com");
        SportResource gym = gym();
        ReservationRequest booking = request(gym, LocalDate.now(WARSAW).plusDays(12), gym.getOpeningTime(), 1);
        booking.setKind(ReservationKind.LESSON);
        booking.setPartySize(4);
        booking.setCoachId(coach.getId());
        booking.setSkillLevel("DRIVER");
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("group lesson");
    }

    @Test
    void courtBookingAttachesMatchingCoachAsExtra() {
        User coach = createCoach("court_coach", "court.coach@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC", "ORTHODOX"), "80.00");
        User player = player("court_hire", "court.hire@example.com");
        SportResource tennis = tennisCourt();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(13));
        LocalTime start = LocalTime.of(10, 0);
        ReservationRequest booking = request(tennis, date, start, 2);
        booking.setCoachId(coach.getId());
        booking.setSkillLevel("CATHOLIC");
        BigDecimal court = pricingService.quote(tennis, date, start, 2, ReservationKind.STANDARD, List.of(), tennis.getMinPartySize());
        Reservation reservation = reservationService.create(player, booking);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCoach().getId()).isEqualTo(coach.getId());
        assertThat(reservation.getSkillLevel()).isEqualTo("CATHOLIC");
        assertThat(reservation.getExtrasSummary()).contains("Coach " + coach.getFullName());
        assertThat(reservation.getExtras().stream()
                .filter(extra -> extra.getDescription() != null && extra.getDescription().startsWith("Coach "))
                .count()).isEqualTo(1);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(court.add(new BigDecimal("160.00")));
        List<Reservation> queue = reservationService.findManagerQueue(date);
        assertThat(queue.stream().anyMatch(item -> item.getId().equals(reservation.getId())
                && item.getExtrasSummary().contains("Coach " + coach.getFullName()))).isTrue();
        assertThat(userSportLevelRepository.findByUser_IdAndSportType(player.getId(), ResourceType.CHAPEL)
                .orElseThrow()
                .getSkillLevel()).isEqualTo("CATHOLIC");
    }

    @Test
    void individualGymBookingAttachesMatchingCoach() {
        User coach = createCoach("gym_coach", "gym.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("BEARER"), "90.00");
        User player = player("gym_hire", "gym.hire@example.com");
        SportResource gym = gym();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(14));
        ReservationRequest booking = request(gym, date, gym.getOpeningTime(), 1);
        booking.setKind(ReservationKind.INDIVIDUAL);
        booking.setCoachId(coach.getId());
        booking.setSkillLevel("BEARER");
        Reservation reservation = reservationService.create(player, booking);
        assertThat(reservation.getKind()).isEqualTo(ReservationKind.INDIVIDUAL);
        assertThat(reservation.getCoach().getId()).isEqualTo(coach.getId());
        assertThat(reservation.getExtrasSummary()).contains("Coach " + coach.getFullName());
    }

    @Test
    void coachWrongLevelCannotBeAttached() {
        User coach = createCoach("strict_coach", "strict.coach@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        User player = player("mismatch_player", "mismatch.player@example.com");
        SportResource tennis = tennisCourt();
        ReservationRequest booking = request(tennis, LocalDate.now(WARSAW).plusDays(15), tennis.getOpeningTime(), 1);
        booking.setCoachId(coach.getId());
        booking.setSkillLevel("MUSLIM");
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("level");
    }

    @Test
    void overlappingCoachAssignmentFails() {
        User coach = createCoach("busy_coach", "busy.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("BEARER"), "90.00");
        User first = player("hire_one", "hire.one@example.com");
        User second = player("hire_two", "hire.two@example.com");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(16);
        LocalTime start = gym.getOpeningTime();
        ReservationRequest one = request(gym, date, start, 2);
        one.setKind(ReservationKind.INDIVIDUAL);
        one.setCoachId(coach.getId());
        one.setSkillLevel("BEARER");
        reservationService.create(first, one);
        ReservationRequest two = request(gym, date, start.plusHours(1), 1);
        two.setKind(ReservationKind.INDIVIDUAL);
        two.setCoachId(coach.getId());
        two.setSkillLevel("BEARER");
        assertThatThrownBy(() -> reservationService.create(second, two))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void chapelLevelsAreDenominationsAndTransportIsBearerRoles() {
        List<String> chapel = sportSkillLevelCatalog.levelsFor(ResourceType.CHAPEL).stream()
                .map(level -> level.getCode())
                .toList();
        assertThat(chapel).contains("CATHOLIC", "ORTHODOX", "MUSLIM")
                .doesNotContain("DRIVER", "BEARER", "CONDUCTOR");
        assertThat(chapel.getFirst()).isEqualTo("CATHOLIC");
        assertThat(chapel.getLast()).isEqualTo("CIVIL");
        List<String> transport = sportSkillLevelCatalog.levelsFor(ResourceType.TRANSPORT).stream()
                .map(level -> level.getCode())
                .toList();
        assertThat(transport).containsExactly("DRIVER", "BEARER", "CONDUCTOR");
        assertThat(transport).doesNotContain("CATHOLIC", "MUSLIM");
    }

    @Test
    void catholicRiteListsOnlyCelebrantsCoveringIt() throws Exception {
        User match = createCoach("ntrp_three", "ntrp.three@example.com");
        User hidden = createCoach("ntrp_four", "ntrp.four@example.com");
        saveOffering(match, ResourceType.CHAPEL, Set.of("CATHOLIC", "ORTHODOX"), "80.00");
        saveOffering(hidden, ResourceType.CHAPEL, Set.of("JEWISH", "MUSLIM"), "110.00");
        List<CoachOffering> matches = coachOfferingService.findPublished(ResourceType.CHAPEL, "CATHOLIC");
        assertThat(matches).extracting(item -> item.getCoach().getUsername())
                .contains("ntrp_three")
                .doesNotContain("ntrp_four");
        List<CoachPickerCard> cards = coachOfferingService.pickerCards(ResourceType.CHAPEL);
        assertThat(cards.stream().filter(card -> card.getLevels().contains("CATHOLIC")).map(CoachPickerCard::getFullName))
                .contains(match.getFullName())
                .doesNotContain(hidden.getFullName());
        User player = player("ntrp_filter", "ntrp.filter@example.com");
        SportResource tennis = tennisCourt();
        ReservationRequest booking = request(tennis, LocalDate.now(WARSAW).plusDays(17), tennis.getOpeningTime(), 1);
        booking.setCoachId(hidden.getId());
        booking.setSkillLevel("CATHOLIC");
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("level");
        mockMvc.perform(get("/resources/" + tennis.getId()).with(user("ntrp_filter").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Select a level first")))
                .andExpect(content().string(containsString("Coach ntrp_three")))
                .andExpect(content().string(containsString("Coach ntrp_four")))
                .andExpect(content().string(containsString("New coach")))
                .andExpect(content().string(containsString("No celebrant")));
    }

    @Test
    void ratingBeforeEndIsRejectedAndPastConfirmedIsAcceptedOnce() {
        User coach = createCoach("rated_coach", "rated.coach@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        User owner = player("rater_one", "rater.one@example.com");
        User other = player("rater_two", "rater.two@example.com");
        User admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        SportResource tennis = tennisCourt();
        LocalDateTime now = LocalDateTime.now(WARSAW);
        Reservation upcoming = persistEnded(owner, tennis, coach, now.plusDays(2), now.plusDays(2).plusHours(1), ReservationStatus.CONFIRMED);
        CoachRatingRequest stars = ratingRequest(5, "Great footwork");
        assertThatThrownBy(() -> coachRatingService.rate(owner, upcoming.getId(), stars))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("ended");
        Reservation pendingPast = persistEnded(owner, tennis, coach, now.minusDays(3), now.minusDays(3).plusHours(1), ReservationStatus.PENDING);
        assertThatThrownBy(() -> coachRatingService.rate(owner, pendingPast.getId(), stars))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("confirmed");
        Reservation past = persistEnded(owner, tennis, coach, now.minusDays(2), now.minusDays(2).plusHours(1), ReservationStatus.CONFIRMED);
        assertThat(coachRatingService.canRate(owner, past)).isTrue();
        assertThat(coachRatingService.canRate(other, past)).isFalse();
        assertThat(coachRatingService.canRate(admin, past)).isFalse();
        CoachRating saved = coachRatingService.rate(owner, past.getId(), stars);
        assertThat(saved.getStars()).isEqualTo(5);
        assertThat(saved.getReview()).isEqualTo("Great footwork");
        assertThatThrownBy(() -> coachRatingService.rate(owner, past.getId(), ratingRequest(4, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("already rated");
        assertThatThrownBy(() -> coachRatingService.rate(other, past.getId(), ratingRequest(1, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("own booking");
        assertThatThrownBy(() -> coachRatingService.rate(admin, past.getId(), ratingRequest(1, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("own booking");
    }

    @Test
    void pickerShowsAverageRatingAndNewCoachLabel() {
        User fresh = createCoach("new_badge", "new.badge@example.com");
        User ranked = createCoach("ranked_badge", "ranked.badge@example.com");
        saveOffering(fresh, ResourceType.CHAPEL, Set.of("CATHOLIC"), "70.00");
        saveOffering(ranked, ResourceType.CHAPEL, Set.of("CATHOLIC"), "90.00");
        User first = player("avg_one", "avg.one@example.com");
        User second = player("avg_two", "avg.two@example.com");
        SportResource tennis = tennisCourt();
        LocalDateTime now = LocalDateTime.now(WARSAW);
        Reservation one = persistEnded(first, tennis, ranked, now.minusDays(5), now.minusDays(5).plusHours(1), ReservationStatus.CONFIRMED);
        Reservation two = persistEnded(second, tennis, ranked, now.minusDays(4), now.minusDays(4).plusHours(1), ReservationStatus.CONFIRMED);
        coachRatingService.rate(first, one.getId(), ratingRequest(5, null));
        coachRatingService.rate(second, two.getId(), ratingRequest(4, "Solid"));
        CoachRatingSummary summary = coachRatingService.summaryFor(ranked.getId());
        assertThat(summary.getCount()).isEqualTo(2);
        assertThat(summary.getAverage()).isEqualTo(4.5);
        assertThat(summary.getDisplayLabel()).isEqualTo("★ 4.5 (2)");
        assertThat(coachRatingService.summaryFor(fresh.getId()).getDisplayLabel()).isEqualTo("New coach");
        List<CoachPickerCard> cards = coachOfferingService.pickerCards(ResourceType.CHAPEL);
        CoachPickerCard rankedCard = cards.stream()
                .filter(card -> ranked.getId().equals(card.getId()))
                .findFirst()
                .orElseThrow();
        CoachPickerCard freshCard = cards.stream()
                .filter(card -> fresh.getId().equals(card.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(rankedCard.getRatingCount()).isEqualTo(2);
        assertThat(rankedCard.getAverageRating()).isEqualTo(4.5);
        assertThat(rankedCard.getRatingLabel()).isEqualTo("★ 4.5 (2)");
        assertThat(freshCard.getRatingLabel()).isEqualTo("New coach");
    }

    private User createCoach(String username, String email) {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName("Coach " + username);
        request.setPhone("+48 555 010 030");
        request.setPassword("Password1");
        request.setConfirmPassword("Password1");
        request.setRole(Role.COACH);
        return userService.createStaff(request);
    }

    private User player(String username, String email) {
        return userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setFullName("Player " + username);
            request.setPhone("+48 555 010 040");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            return userService.register(request);
        });
    }

    private void saveOffering(User coach, ResourceType sport, Set<String> levels, String price) {
        CoachOfferingRequest request = new CoachOfferingRequest();
        request.setSportType(sport);
        request.setLevels(new LinkedHashSet<>(levels));
        request.setPricePerHour(new BigDecimal(price));
        coachOfferingService.save(coach, request);
    }

    private SportResource tennisCourt() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.CHAPEL && resource.isEnabled() && resource.requiresPartySize())
                .findFirst()
                .orElseThrow();
    }

    private SportResource gym() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.TRANSPORT && resource.isEnabled() && !resource.requiresPartySize())
                .findFirst()
                .orElseThrow();
    }

    private ReservationRequest request(SportResource resource, LocalDate date, LocalTime startTime, int durationHours) {
        ReservationRequest request = new ReservationRequest();
        request.setResourceId(resource.getId());
        request.setDate(date);
        request.setStartTime(startTime);
        request.setDurationHours(durationHours);
        request.setPaymentMethod(PaymentMethod.CASH);
        if (resource.requiresPartySize()) {
            request.setPartySize(resource.getMinPartySize());
        }
        return request;
    }

    private CoachRatingRequest ratingRequest(int stars, String review) {
        CoachRatingRequest request = new CoachRatingRequest();
        request.setStars(stars);
        request.setReview(review);
        return request;
    }

    private Reservation persistEnded(
            User user,
            SportResource court,
            User coach,
            LocalDateTime startAt,
            LocalDateTime endAt,
            ReservationStatus status
    ) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setResource(court);
        reservation.setCoach(coach);
        reservation.setSkillLevel("CATHOLIC");
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(status);
        reservation.setPartySize(1);
        reservation.setPaymentMethod(PaymentMethod.CASH);
        reservation.setTotalAmount(BigDecimal.ZERO.setScale(2));
        reservation.setKind(ReservationKind.STANDARD);
        reservation.setOccupancyUnits(1);
        return reservationRepository.saveAndFlush(reservation);
    }

    private LocalDate nextWeekday(LocalDate start) {
        LocalDate cursor = start;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY || cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }
}
