package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.CoachOfferingRequest;
import com.example.hackathoncodaro2026.dto.CoachRatingRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.CoachOffering;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.AvatarStorageService;
import com.example.hackathoncodaro2026.service.CoachOfferingService;
import com.example.hackathoncodaro2026.service.CoachRatingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachOfferingRatingAndWebTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoachOfferingService coachOfferingService;

    @Autowired
    private CoachRatingService coachRatingService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private AvatarStorageService avatarStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void offeringRejectsZeroPriceDuplicateSportAndForeignOwnership() {
        User coach = createCoach("own_coach", "own.coach@example.com");
        User other = createCoach("other_coach", "other.coach@example.com");
        CoachOfferingRequest create = offering(ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        CoachOffering saved = coachOfferingService.save(coach, create);
        CoachOfferingRequest duplicate = offering(ResourceType.CHAPEL, Set.of("ORTHODOX"), "90.00");
        assertThatThrownBy(() -> coachOfferingService.save(coach, duplicate))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("already have");
        CoachOfferingRequest free = offering(ResourceType.TRANSPORT, Set.of("DRIVER"), "0.00");
        assertThatThrownBy(() -> coachOfferingService.save(coach, free))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("price");
        assertThatThrownBy(() -> coachOfferingService.delete(other, saved.getId()))
                .isInstanceOf(ReservationException.class);
        coachOfferingService.delete(coach, saved.getId());
        assertThat(coachOfferingService.findForCoach(coach, saved.getId())).isEmpty();
    }

    @Test
    void coachWrongSportIsRejectedAndAdjacentSlotsAreAllowed() {
        User coach = createCoach("span_coach", "span.coach@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        User player = player("span_player", "span.player@example.com");
        SportResource tennis = tennisCourt();
        SportResource gym = gym();
        ReservationRequest gymHire = booking(gym, LocalDate.now(WARSAW).plusDays(56), gym.getOpeningTime());
        gymHire.setKind(ReservationKind.INDIVIDUAL);
        gymHire.setCoachId(coach.getId());
        gymHire.setSkillLevel("DRIVER");
        assertThatThrownBy(() -> reservationService.create(player, gymHire))
                .isInstanceOf(ReservationException.class);
        LocalDate date = LocalDate.now(WARSAW).plusDays(57);
        ReservationRequest first = booking(tennis, date, tennis.getOpeningTime());
        first.setCoachId(coach.getId());
        first.setSkillLevel("CATHOLIC");
        ReservationRequest second = booking(tennis, date, tennis.getOpeningTime().plusHours(1));
        second.setCoachId(coach.getId());
        second.setSkillLevel("CATHOLIC");
        Reservation a = reservationService.create(player, first);
        Reservation b = reservationService.create(player, second);
        assertThat(a.getEndAt()).isEqualTo(b.getStartAt());
        assertThat(reservationService.findForCoach(coach)).extracting(Reservation::getId)
                .contains(a.getId(), b.getId());
    }

    @Test
    void ratingRejectsInvalidStarsLongReviewMissingCoachAndOutOfRange() {
        User coach = createCoach("rate_edge", "rate.edge@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        User owner = player("rate_owner", "rate.owner@example.com");
        SportResource tennis = tennisCourt();
        LocalDateTime now = LocalDateTime.now(WARSAW);
        Reservation withCoach = persistEnded(owner, tennis, coach, now.minusDays(2), now.minusDays(2).plusHours(1), ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> coachRatingService.rate(owner, withCoach.getId(), rating(0, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("1 to 5");
        assertThatThrownBy(() -> coachRatingService.rate(owner, withCoach.getId(), rating(6, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("1 to 5");
        assertThatThrownBy(() -> coachRatingService.rate(owner, withCoach.getId(), rating(5, "x".repeat(501))))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("500");
        Reservation noCoach = persistEnded(owner, tennis, null, now.minusDays(3), now.minusDays(3).plusHours(1), ReservationStatus.CONFIRMED);
        assertThat(coachRatingService.canRate(owner, noCoach)).isFalse();
        assertThatThrownBy(() -> coachRatingService.rate(owner, noCoach.getId(), rating(5, null)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("coach");
    }

    @Test
    void avatarRejectsInvalidTypeAndServesPlaceholder() throws Exception {
        User player = player("avatar_user", "avatar.user@example.com");
        MockMultipartFile invalid = new MockMultipartFile(
                "avatar",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not-an-image".getBytes()
        );
        assertThatThrownBy(() -> avatarStorageService.store(player, invalid))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("JPG");
        MockMultipartFile empty = new MockMultipartFile("avatar", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);
        avatarStorageService.store(player, empty);
        assertThat(player.getAvatarFilename()).isNull();
        mockMvc.perform(get("/avatars/" + player.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/svg+xml")));
    }

    @Test
    void coachPagesAndCoreRoutesRenderForAllowedRoles() throws Exception {
        User coach = createCoach("web_coach", "web.coach@example.com");
        saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
        User player = player("web_player", "web.player@example.com");
        SportResource tennis = tennisCourt();
        mockMvc.perform(get("/facilities").with(user("web_player").roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/facilities/" + tennis.getFacility().getId()).with(user("web_player").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(tennis.getFacility().getName())));
        mockMvc.perform(get("/resources/" + tennis.getId()).with(user("web_player").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(tennis.getName())));
        mockMvc.perform(get("/occupancy").with(user("web_player").roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reservations").with(user("web_player").roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/coach/offerings").with(user("web_coach").roles("COACH")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chapel ceremony")));
        mockMvc.perform(get("/coach/sessions").with(user("web_coach").roles("COACH")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/").with(user("web_player").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void managerConfirmAndOwnerCancelPostsKeepAuthorization() throws Exception {
        User player = player("post_owner", "post.owner@example.com");
        SportResource court = exclusiveCourt();
        Reservation pending = reservationService.create(
                player,
                booking(court, LocalDate.now(WARSAW).plusDays(58), court.getOpeningTime())
        );
        mockMvc.perform(post("/manager/reservations/" + pending.getId() + "/confirm")
                        .with(user("post_owner").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/manager/reservations/" + pending.getId() + "/confirm")
                        .param("date", pending.getStartAt().toLocalDate().toString())
                        .with(user("manager").roles("MANAGER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/manager/reservations**"));
        assertThat(reservationRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        Reservation cancellable = reservationService.create(
                player,
                booking(court, LocalDate.now(WARSAW).plusDays(59), court.getOpeningTime().plusHours(2))
        );
        mockMvc.perform(post("/reservations/" + cancellable.getId() + "/cancel")
                        .param("reason", "CHANGE_OF_PLANS")
                        .with(user("post_owner").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservations"));
        assertThat(reservationRepository.findById(cancellable.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void occupancyAcceptsDateAndFacilityQuery() throws Exception {
        SportResource court = exclusiveCourt();
        mockMvc.perform(get("/occupancy")
                        .param("date", LocalDate.now(WARSAW).plusDays(60).toString())
                        .param("facilityId", String.valueOf(court.getFacility().getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/manager/reservations").param("date", LocalDate.now(WARSAW).plusDays(60).toString()))
                .andExpect(status().isOk());
    }

    private User createCoach(String username, String email) {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName("Coach " + username);
        request.setPhone("+48 555 010 117");
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
            request.setPhone("+48 555 010 118");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            return userService.register(request);
        });
    }

    private void saveOffering(User coach, ResourceType sport, Set<String> levels, String price) {
        coachOfferingService.save(coach, offering(sport, levels, price));
    }

    private CoachOfferingRequest offering(ResourceType sport, Set<String> levels, String price) {
        CoachOfferingRequest request = new CoachOfferingRequest();
        request.setSportType(sport);
        request.setLevels(new LinkedHashSet<>(levels));
        request.setPricePerHour(new BigDecimal(price));
        return request;
    }

    private CoachRatingRequest rating(int stars, String review) {
        CoachRatingRequest request = new CoachRatingRequest();
        request.setStars(stars);
        request.setReview(review);
        return request;
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

    private SportResource exclusiveCourt() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getCapacity() == 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
    }

    private ReservationRequest booking(SportResource resource, LocalDate date, LocalTime start) {
        ReservationRequest request = new ReservationRequest();
        request.setResourceId(resource.getId());
        request.setDate(date);
        request.setStartTime(start);
        request.setDurationHours(1);
        request.setPaymentMethod(PaymentMethod.CASH);
        if (resource.requiresPartySize()) {
            request.setPartySize(resource.getMinPartySize());
        }
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
        reservation.setSkillLevel(coach == null ? null : "CATHOLIC");
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
}
