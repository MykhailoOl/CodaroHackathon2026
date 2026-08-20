package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.CoachOfferingRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.dto.ReservationUpdateResult;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.InventoryItem;
import com.example.hackathoncodaro2026.model.Notification;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.NotificationType;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.InventoryItemRepository;
import com.example.hackathoncodaro2026.repository.NotificationRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.CoachOfferingService;
import com.example.hackathoncodaro2026.service.NotificationService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.ResourceService;
import com.example.hackathoncodaro2026.service.UserService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@Transactional
class ReservationEditServiceTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private CoachOfferingService coachOfferingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ResourceService resourceService;

    @Test
    void ownerCanUpdatePendingReservationWithoutCreatingAnother() {
        User player = player("edit_keep_owner", "edit.keep.owner@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(40);
        Reservation created = reservationService.create(player, request(court, date, court.getOpeningTime(), 1));
        long before = reservationRepository.count();
        ReservationRequest change = request(court, date, court.getOpeningTime().plusHours(2), 1);
        change.setPaymentMethod(PaymentMethod.CARD_ON_SITE);
        ReservationUpdateResult result = reservationService.update(player, created.getId(), change);
        assertThat(result.reservation().getId()).isEqualTo(created.getId());
        assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.reservation().getStartAt().toLocalTime()).isEqualTo(court.getOpeningTime().plusHours(2));
        assertThat(result.reservation().getPaymentMethod()).isEqualTo(PaymentMethod.CARD_ON_SITE);
        assertThat(reservationRepository.count()).isEqualTo(before);
        assertThat(reservationService.canEdit(player, result.reservation())).isTrue();
    }

    @Test
    void otherUserCannotUpdatePendingReservation() {
        User owner = player("edit_owner_guard", "edit.owner.guard@example.com");
        User other = player("edit_other_guard", "edit.other.guard@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(41);
        Reservation created = reservationService.create(owner, request(court, date, court.getOpeningTime(), 1));
        LocalDateTime originalStart = created.getStartAt();
        ReservationRequest change = request(court, date, court.getOpeningTime().plusHours(1), 1);
        assertThatThrownBy(() -> reservationService.update(other, created.getId(), change))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("could not be found");
        Reservation stored = reservationRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getStartAt()).isEqualTo(originalStart);
        assertThat(stored.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void confirmedCancelledAndPastPendingCannotBeUpdated() {
        User owner = player("edit_status_owner", "edit.status.owner@example.com");
        User manager = userRepository.findByUsernameIgnoreCase("manager").orElseThrow();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(42);
        Reservation pending = reservationService.create(owner, request(court, date, court.getOpeningTime(), 1));
        reservationService.confirm(manager, pending.getId());
        ReservationRequest move = request(court, date, court.getOpeningTime().plusHours(3), 1);
        assertThatThrownBy(() -> reservationService.update(owner, pending.getId(), move))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Only pending reservations can be changed");
        assertThat(reservationRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservationRepository.findById(pending.getId()).orElseThrow().getStartAt())
                .isEqualTo(date.atTime(court.getOpeningTime()));

        Reservation cancellable = reservationService.create(
                owner,
                request(court, date, court.getOpeningTime().plusHours(2), 1)
        );
        reservationService.cancel(owner, cancellable.getId(), "CHANGE_OF_PLANS");
        assertThatThrownBy(() -> reservationService.update(owner, cancellable.getId(), move))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Only pending reservations can be changed");
        assertThat(reservationRepository.findById(cancellable.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);

        LocalDateTime now = LocalDateTime.now(WARSAW);
        Reservation past = persistPending(owner, court, now.minusDays(2), now.minusDays(2).plusHours(1));
        BigDecimal pastAmount = past.getTotalAmount();
        assertThatThrownBy(() -> reservationService.update(owner, past.getId(), request(court, date, court.getOpeningTime(), 1)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("no longer be changed");
        Reservation storedPast = reservationRepository.findById(past.getId()).orElseThrow();
        assertThat(storedPast.getStartAt()).isEqualTo(past.getStartAt());
        assertThat(storedPast.getTotalAmount()).isEqualByComparingTo(pastAmount);
    }

    @Test
    void concurrentStatusChangeRejectsEditAndKeepsOriginal() {
        User owner = player("edit_race_owner", "edit.race.owner@example.com");
        User manager = userRepository.findByUsernameIgnoreCase("manager").orElseThrow();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(43);
        Reservation created = reservationService.create(owner, request(court, date, court.getOpeningTime(), 1));
        LocalDateTime originalStart = created.getStartAt();
        reservationService.confirm(manager, created.getId());
        ReservationRequest change = request(court, date, court.getOpeningTime().plusHours(1), 2);
        assertThatThrownBy(() -> reservationService.update(owner, created.getId(), change))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Only pending reservations can be changed");
        Reservation stored = reservationRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored.getStartAt()).isEqualTo(originalStart);
        assertThat(stored.getEndAt()).isEqualTo(originalStart.plusHours(1));
    }

    @Test
    void dateDurationEditRecalculatesEveningAndWeekendAmount() {
        User player = player("edit_price_owner", "edit.price.owner@example.com");
        SportResource tennis = tennisCourt();
        LocalDate weekday = nextWeekday(LocalDate.now(WARSAW).plusDays(44));
        LocalDate weekend = nextSaturday(weekday.plusDays(1));
        LocalTime morning = LocalTime.of(10, 0);
        Reservation created = reservationService.create(player, request(tennis, weekday, morning, 1));
        BigDecimal morningAmount = pricingService.quote(tennis, weekday, morning, 1);
        assertThat(created.getTotalAmount()).isEqualByComparingTo(morningAmount);
        LocalTime evening = LocalTime.of(18, 0);
        ReservationRequest change = request(tennis, weekend, evening, 2);
        ReservationUpdateResult result = reservationService.update(player, created.getId(), change);
        BigDecimal expected = pricingService.quote(tennis, weekend, evening, 2);
        assertThat(result.newAmount()).isEqualByComparingTo(expected);
        assertThat(result.previousAmount()).isEqualByComparingTo(morningAmount);
        assertThat(result.amountChanged()).isTrue();
        assertThat(result.reservation().getTotalAmount()).isEqualByComparingTo(expected);
        Notification notice = latest(player, NotificationType.RESERVATION_UPDATED);
        assertThat(notice.getMessage()).contains(morningAmount.setScale(2).toPlainString() + " PLN");
        assertThat(notice.getMessage()).contains(expected.setScale(2).toPlainString() + " PLN");
    }

    @Test
    void extrasAndPartyChangesRecalculatePerAttendee() {
        User player = player("edit_extra_owner", "edit.extra.owner@example.com");
        SportResource tennis = tennisCourt();
        InventoryItem racket = racket();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(46));
        LocalTime start = LocalTime.of(10, 0);
        ReservationRequest create = request(tennis, date, start, 1);
        create.setPartySize(2);
        Reservation created = reservationService.create(player, create);
        BigDecimal withoutExtra = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(), 2);
        assertThat(created.getTotalAmount()).isEqualByComparingTo(withoutExtra);
        ReservationRequest change = request(tennis, date, start, 1);
        change.setPartySize(3);
        change.setExtraIds(List.of(racket.getId()));
        ReservationUpdateResult result = reservationService.update(player, created.getId(), change);
        BigDecimal expected = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(racket), 3);
        assertThat(result.reservation().getPartySize()).isEqualTo(3);
        assertThat(result.reservation().getTotalAmount()).isEqualByComparingTo(expected);
        assertThat(result.reservation().getExtrasSummary()).contains("Floral tribute ×3");
        assertThat(result.amountChanged()).isTrue();
    }

    @Test
    void removingCoachFreesTimeNotifiesAndDropsFee() {
        User owner = player("edit_drop_owner", "edit.drop.owner@example.com");
        User other = player("edit_drop_other", "edit.drop.other@example.com");
        User coach = createCoach("edit_drop_coach", "edit.drop.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("BEARER"), "90.00");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(47);
        LocalTime start = gym.getOpeningTime();
        ReservationRequest create = request(gym, date, start, 1);
        create.setKind(ReservationKind.INDIVIDUAL);
        create.setCoachId(coach.getId());
        create.setSkillLevel("BEARER");
        Reservation created = reservationService.create(owner, create);
        BigDecimal withCoach = created.getTotalAmount();
        ReservationRequest change = request(gym, date, start, 1);
        change.setKind(ReservationKind.INDIVIDUAL);
        change.setCoachId(null);
        ReservationUpdateResult result = reservationService.update(owner, created.getId(), change);
        assertThat(result.reservation().getCoach()).isNull();
        assertThat(result.reservation().getExtras().stream().noneMatch(extra ->
                extra.getDescription() != null && extra.getDescription().startsWith("Coach "))).isTrue();
        BigDecimal withoutCoach = pricingService.quote(gym, date, start, 1, ReservationKind.INDIVIDUAL, List.of(), 1);
        assertThat(result.reservation().getTotalAmount()).isEqualByComparingTo(withoutCoach);
        assertThat(withCoach).isGreaterThan(withoutCoach);
        assertThat(reservationService.findForCoach(coach)).noneMatch(item -> item.getId().equals(created.getId()));
        Notification removed = latest(coach, NotificationType.COACH_REMOVED);
        assertThat(removed.getMessage()).contains("available");
        assertThat(removed.getMessage()).doesNotContain(owner.getEmail());
        assertThat(removed.getMessage()).doesNotContain(owner.getPhone());
        ReservationRequest reuse = request(gym, date, start.plusHours(2), 1);
        reuse.setKind(ReservationKind.INDIVIDUAL);
        reuse.setCoachId(coach.getId());
        reuse.setSkillLevel("BEARER");
        Reservation second = reservationService.create(other, reuse);
        assertThat(second.getCoach().getId()).isEqualTo(coach.getId());
        ReservationRequest sameSlot = request(gym, date, start, 1);
        sameSlot.setKind(ReservationKind.INDIVIDUAL);
        sameSlot.setCoachId(coach.getId());
        sameSlot.setSkillLevel("BEARER");
        Reservation reusedSlot = reservationService.create(other, sameSlot);
        assertThat(reusedSlot.getCoach().getId()).isEqualTo(coach.getId());
        assertThat(reusedSlot.getStartAt()).isEqualTo(date.atTime(start));
    }

    @Test
    void changingCoachNotifiesBothAndUsesNewFee() {
        User owner = player("edit_swap_owner", "edit.swap.owner@example.com");
        User oldCoach = createCoach("edit_swap_old", "edit.swap.old@example.com");
        User newCoach = createCoach("edit_swap_new", "edit.swap.new@example.com");
        saveOffering(oldCoach, ResourceType.TRANSPORT, Set.of("BEARER"), "90.00");
        saveOffering(newCoach, ResourceType.TRANSPORT, Set.of("BEARER"), "120.00");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(48);
        LocalTime start = gym.getOpeningTime();
        ReservationRequest create = request(gym, date, start, 1);
        create.setKind(ReservationKind.INDIVIDUAL);
        create.setCoachId(oldCoach.getId());
        create.setSkillLevel("BEARER");
        Reservation created = reservationService.create(owner, create);
        ReservationRequest change = request(gym, date, start, 1);
        change.setKind(ReservationKind.INDIVIDUAL);
        change.setCoachId(newCoach.getId());
        change.setSkillLevel("BEARER");
        ReservationUpdateResult result = reservationService.update(owner, created.getId(), change);
        assertThat(result.reservation().getCoach().getId()).isEqualTo(newCoach.getId());
        BigDecimal expected = pricingService.quote(
                gym,
                date,
                start,
                1,
                ReservationKind.INDIVIDUAL,
                List.of(),
                1,
                new BigDecimal("120.00")
        );
        assertThat(result.reservation().getTotalAmount()).isEqualByComparingTo(expected);
        assertThat(reservationService.findForCoach(oldCoach)).noneMatch(item -> item.getId().equals(created.getId()));
        assertThat(reservationService.findForCoach(newCoach)).anyMatch(item -> item.getId().equals(created.getId()));
        assertThat(latest(oldCoach, NotificationType.COACH_REMOVED).getMessage()).contains("available");
        assertThat(latest(newCoach, NotificationType.COACH_ASSIGNED).getMessage()).contains("assigned");
    }

    @Test
    void sameCoachMovedTimeIsOverlapCheckedAndNotified() {
        User owner = player("edit_move_owner", "edit.move.owner@example.com");
        User other = player("edit_move_other", "edit.move.other@example.com");
        User coach = createCoach("edit_move_coach", "edit.move.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("BEARER"), "90.00");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(49);
        LocalTime start = gym.getOpeningTime();
        ReservationRequest blocking = request(gym, date, start.plusHours(3), 1);
        blocking.setKind(ReservationKind.INDIVIDUAL);
        blocking.setCoachId(coach.getId());
        blocking.setSkillLevel("BEARER");
        reservationService.create(other, blocking);
        ReservationRequest create = request(gym, date, start, 1);
        create.setKind(ReservationKind.INDIVIDUAL);
        create.setCoachId(coach.getId());
        create.setSkillLevel("BEARER");
        Reservation created = reservationService.create(owner, create);
        LocalDateTime originalStart = created.getStartAt();
        ReservationRequest overlap = request(gym, date, start.plusHours(3), 1);
        overlap.setKind(ReservationKind.INDIVIDUAL);
        overlap.setCoachId(coach.getId());
        overlap.setSkillLevel("BEARER");
        assertThatThrownBy(() -> reservationService.update(owner, created.getId(), overlap))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("no longer available");
        Reservation unchanged = reservationRepository.findById(created.getId()).orElseThrow();
        assertThat(unchanged.getStartAt()).isEqualTo(originalStart);
        assertThat(unchanged.getCoach().getId()).isEqualTo(coach.getId());
        ReservationRequest moved = request(gym, date, start.plusHours(1), 1);
        moved.setKind(ReservationKind.INDIVIDUAL);
        moved.setCoachId(coach.getId());
        moved.setSkillLevel("BEARER");
        ReservationUpdateResult result = reservationService.update(owner, created.getId(), moved);
        assertThat(result.reservation().getCoach().getId()).isEqualTo(coach.getId());
        assertThat(result.reservation().getStartAt()).isEqualTo(date.atTime(start.plusHours(1)));
        Notification shifted = latest(coach, NotificationType.COACH_SCHEDULE_CHANGED);
        assertThat(shifted.getMessage()).contains("moved");
    }

    @Test
    void ownUnchangedIntervalDoesNotConflictAndIgnoresResourceChange() {
        User player = player("edit_self_owner", "edit.self.owner@example.com");
        SportResource court = exclusiveCourt();
        SportResource other = tennisCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(50);
        Reservation created = reservationService.create(player, request(court, date, court.getOpeningTime(), 1));
        ReservationRequest same = request(court, date, court.getOpeningTime(), 1);
        same.setResourceId(other.getId());
        ReservationUpdateResult result = reservationService.update(player, created.getId(), same);
        assertThat(result.reservation().getResource().getId()).isEqualTo(court.getId());
        assertThat(result.reservation().getStartAt()).isEqualTo(created.getStartAt());
        assertThat(result.amountChanged()).isFalse();
        Notification notice = latest(player, NotificationType.RESERVATION_UPDATED);
        assertThat(notice.getMessage()).contains("remains");
        assertThat(resourceService.slotsFor(court, date, ReservationKind.STANDARD, created.getId()).stream()
                .anyMatch(slot -> slot.getStart().equals(court.getOpeningTime()) && slot.isAvailable())).isTrue();
    }

    @Test
    void lessonEditKeepsFullCapacityAndForbidsCoach() {
        User player = player("edit_lesson_owner", "edit.lesson.owner@example.com");
        User coach = createCoach("edit_lesson_coach", "edit.lesson.coach@example.com");
        saveOffering(coach, ResourceType.TRANSPORT, Set.of("DRIVER"), "90.00");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(51);
        ReservationRequest create = request(gym, date, gym.getOpeningTime(), 1);
        create.setKind(ReservationKind.LESSON);
        create.setPartySize(4);
        Reservation created = reservationService.create(player, create);
        assertThat(created.getOccupancyUnits()).isEqualTo(gym.getCapacity());
        ReservationRequest withCoach = request(gym, date, gym.getOpeningTime(), 1);
        withCoach.setKind(ReservationKind.LESSON);
        withCoach.setPartySize(5);
        withCoach.setCoachId(coach.getId());
        withCoach.setSkillLevel("DRIVER");
        assertThatThrownBy(() -> reservationService.update(player, created.getId(), withCoach))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("group lesson");
        Reservation stored = reservationRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getPartySize()).isEqualTo(4);
        assertThat(stored.getCoach()).isNull();
        ReservationRequest change = request(gym, date, gym.getOpeningTime(), 1);
        change.setKind(ReservationKind.INDIVIDUAL);
        change.setPartySize(6);
        ReservationUpdateResult result = reservationService.update(player, created.getId(), change);
        assertThat(result.reservation().getKind()).isEqualTo(ReservationKind.LESSON);
        assertThat(result.reservation().getPartySize()).isEqualTo(6);
        assertThat(result.reservation().getOccupancyUnits()).isEqualTo(gym.getCapacity());
        assertThat(result.reservation().getCoach()).isNull();
    }

    @Test
    void notificationReadIsOwnerOnlyAndUnreadCountDrops() {
        User owner = player("edit_notice_owner", "edit.notice.owner@example.com");
        User other = player("edit_notice_other", "edit.notice.other@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(52);
        Reservation created = reservationService.create(owner, request(court, date, court.getOpeningTime(), 1));
        reservationService.update(owner, created.getId(), request(court, date, court.getOpeningTime().plusHours(1), 1));
        assertThat(notificationService.unreadCount(owner)).isGreaterThanOrEqualTo(1);
        Notification notice = latest(owner, NotificationType.RESERVATION_UPDATED);
        assertThatThrownBy(() -> notificationService.markRead(other, notice.getId()))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("could not be found");
        assertThat(notificationRepository.findById(notice.getId()).orElseThrow().isRead()).isFalse();
        notificationService.markRead(owner, notice.getId());
        assertThat(notificationRepository.findById(notice.getId()).orElseThrow().isRead()).isTrue();
        notificationService.markAllRead(owner);
        assertThat(notificationService.unreadCount(owner)).isEqualTo(0);
        assertThat(notificationService.findFor(other)).isEmpty();
    }

    @Test
    void updateAuditOmitsPhoneEmailAndNoteValues() {
        User player = player("edit_audit_owner", "edit.audit.owner@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(53);
        Reservation created = reservationService.create(player, request(court, date, court.getOpeningTime(), 1));
        ReservationRequest change = request(court, date, court.getOpeningTime().plusHours(1), 1);
        change.setNote("secret note with " + player.getEmail() + " and " + player.getPhone());
        change.setPhone(player.getPhone());
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            reservationService.update(player, created.getId(), change);
            String joined = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + " " + right);
            assertThat(joined).contains("event=RESERVATION_UPDATE");
            assertThat(joined).contains("result=SUCCESS");
            assertThat(joined).contains("changedFields=");
            assertThat(joined).doesNotContain(player.getEmail());
            assertThat(joined).doesNotContain(player.getPhone());
            assertThat(joined).doesNotContain("secret note");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private Notification latest(User recipient, NotificationType type) {
        return notificationService.findFor(recipient).stream()
                .filter(item -> item.getType() == type)
                .findFirst()
                .orElseThrow();
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

    private User createCoach(String username, String email) {
        return userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            AdminUserCreateRequest request = new AdminUserCreateRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setFullName("Coach " + username);
            request.setPhone("+48 555 010 030");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            request.setRole(Role.COACH);
            return userService.createStaff(request);
        });
    }

    private void saveOffering(User coach, ResourceType sport, Set<String> levels, String price) {
        CoachOfferingRequest request = new CoachOfferingRequest();
        request.setSportType(sport);
        request.setLevels(new LinkedHashSet<>(levels));
        request.setPricePerHour(new BigDecimal(price));
        coachOfferingService.save(coach, request);
    }

    private SportResource exclusiveCourt() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getCapacity() == 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
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

    private InventoryItem racket() {
        return inventoryItemRepository.findByResourceTypeAndEnabledTrueOrderByNameAsc(ResourceType.CHAPEL).stream()
                .filter(item -> item.getName().equalsIgnoreCase("Floral tribute"))
                .findFirst()
                .orElseThrow();
    }

    private Reservation persistPending(User user, SportResource court, LocalDateTime startAt, LocalDateTime endAt) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setResource(court);
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPartySize(1);
        reservation.setPaymentMethod(PaymentMethod.CASH);
        reservation.setTotalAmount(BigDecimal.TEN.setScale(2));
        reservation.setKind(ReservationKind.STANDARD);
        reservation.setOccupancyUnits(1);
        return reservationRepository.saveAndFlush(reservation);
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

    private LocalDate nextWeekday(LocalDate start) {
        LocalDate cursor = start;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY || cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private LocalDate nextSaturday(LocalDate start) {
        LocalDate cursor = start;
        while (cursor.getDayOfWeek() != DayOfWeek.SATURDAY) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }
}
