package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.InventoryItem;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.repository.InventoryItemRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.OccupancyService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReservationServiceTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private UserService userService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void rejectsOverlappingBookingWhenCapacityIsFull() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        ReservationRequest request = request(court, LocalDate.now(WARSAW).plusDays(1), court.getOpeningTime());
        reservationService.create(admin, request);
        assertThatThrownBy(() -> reservationService.create(admin, request))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void rejectsBookingInThePast() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        ReservationRequest request = request(court, LocalDate.now(WARSAW).minusDays(1), court.getOpeningTime());
        assertThatThrownBy(() -> reservationService.create(admin, request))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("past");
    }

    @Test
    void gymAllowsBookingsUpToCapacity() {
        User admin = admin();
        SportResource gym = sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getCapacity() > 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
        LocalDate date = LocalDate.now(WARSAW).plusDays(2);
        ReservationRequest first = request(gym, date, gym.getOpeningTime());
        ReservationRequest second = request(gym, date, gym.getOpeningTime());
        reservationService.create(admin, first);
        reservationService.create(admin, second);
        assertThat(reservationRepository.countOverlapping(
                gym.getId(),
                ReservationStatus.occupying(),
                date.atTime(gym.getOpeningTime()),
                date.atTime(gym.getOpeningTime()).plusMinutes(gym.getSlotDurationMinutes())
        )).isEqualTo(2);
    }

    @Test
    void cancelOwnFutureReservation() {
        User player = player();
        SportResource court = exclusiveCourt();
        var reservation = reservationService.create(
                player,
                request(court, LocalDate.now(WARSAW).plusDays(3), court.getOpeningTime(), 1)
        );
        reservationService.cancel(player, reservation.getId(), "CHANGE_OF_PLANS");
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void twoHourBookingSucceedsWhenRangeIsFree() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(4);
        var reservation = reservationService.create(admin, request(court, date, court.getOpeningTime(), 2));
        assertThat(reservation.getEndAt()).isEqualTo(date.atTime(court.getOpeningTime()).plusHours(2));
    }

    @Test
    void twoHourBookingRejectedWhenMiddleSlotIsFull() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(5);
        LocalTime middle = court.getOpeningTime().plusHours(1);
        reservationService.create(admin, request(court, date, middle, 1));
        assertThatThrownBy(() -> reservationService.create(admin, request(court, date, court.getOpeningTime(), 2)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void durationPastClosingIsRejected() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(6);
        assertThatThrownBy(() -> reservationService.create(admin, request(court, date, LocalTime.of(21, 0), 2)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("opening hours");
    }

    @Test
    void cancelStoresOptionalReason() {
        User player = player();
        SportResource court = exclusiveCourt();
        var reservation = reservationService.create(
                player,
                request(court, LocalDate.now(WARSAW).plusDays(7), court.getOpeningTime(), 1)
        );
        reservationService.cancel(player, reservation.getId(), "SCHEDULING_CONFLICT");
        var stored = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).isEqualTo("Scheduling conflict");
    }

    @Test
    void userBookingStartsPendingAndOccupiesTheSlot() {
        User player = player();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(8);
        var reservation = reservationService.create(player, request(court, date, court.getOpeningTime(), 1));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        var grid = occupancyService.gridFor(date, null);
        var row = grid.getRows().stream()
                .filter(item -> item.getResourceId().equals(court.getId()))
                .findFirst()
                .orElseThrow();
        var cell = row.getCells().stream()
                .filter(item -> item.getStart().equals(court.getOpeningTime()))
                .findFirst()
                .orElseThrow();
        assertThat(cell.getBooked()).isGreaterThanOrEqualTo(1);
        assertThat(cell.isBookable()).isFalse();
    }

    @Test
    void adminAndManagerBookingsAreConfirmed() {
        User admin = admin();
        User manager = userRepository.findByUsernameIgnoreCase("manager").orElseThrow();
        SportResource court = exclusiveCourt();
        var adminBooking = reservationService.create(
                admin,
                request(court, LocalDate.now(WARSAW).plusDays(12), court.getOpeningTime(), 1)
        );
        var managerBooking = reservationService.create(
                manager,
                request(court, LocalDate.now(WARSAW).plusDays(12), court.getOpeningTime().plusHours(2), 1)
        );
        assertThat(adminBooking.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(managerBooking.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void managerQueueListsSoonestUpcomingFirst() {
        User player = player();
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(13);
        var far = reservationService.create(player, request(court, date, court.getOpeningTime().plusHours(2), 1));
        var near = reservationService.create(player, request(court, date, court.getOpeningTime(), 1));
        List<Reservation> queue = reservationService.findManagerQueue(date);
        assertThat(queue).extracting(Reservation::getId).containsExactly(near.getId(), far.getId());
    }

    @Test
    void managerQueueShowsOnlySelectedDate() {
        User player = player();
        SportResource court = exclusiveCourt();
        LocalDate shown = LocalDate.now(WARSAW).plusDays(14);
        LocalDate hidden = LocalDate.now(WARSAW).plusDays(15);
        var onShown = reservationService.create(player, request(court, shown, court.getOpeningTime(), 1));
        var onHidden = reservationService.create(player, request(court, hidden, court.getOpeningTime(), 1));
        List<Reservation> queue = reservationService.findManagerQueue(shown);
        assertThat(queue).extracting(Reservation::getId).contains(onShown.getId()).doesNotContain(onHidden.getId());
        assertThat(reservationService.findManagerQueue(hidden)).extracting(Reservation::getId).contains(onHidden.getId());
    }

    @Test
    void tennisBookingWithoutPartySizeFails() {
        User player = player();
        SportResource tennis = tennisCourt();
        ReservationRequest booking = request(tennis, LocalDate.now(WARSAW).plusDays(18), tennis.getOpeningTime(), 1);
        booking.setPartySize(null);
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("how many people");
    }

    @Test
    void tennisPartySizeOutsideRangeFails() {
        User player = player();
        SportResource tennis = tennisCourt();
        ReservationRequest booking = request(tennis, LocalDate.now(WARSAW).plusDays(19), tennis.getOpeningTime(), 1);
        booking.setPartySize(tennis.getMaxPartySize() + 1);
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("between");
    }

    @Test
    void gymBookingWithoutPartySizePersistsOne() {
        User player = player();
        SportResource gym = gym();
        ReservationRequest booking = request(gym, LocalDate.now(WARSAW).plusDays(20), gym.getOpeningTime(), 1);
        booking.setPartySize(null);
        var reservation = reservationService.create(player, booking);
        assertThat(reservation.getPartySize()).isEqualTo(1);
    }

    @Test
    void paymentMethodIsRequired() {
        User player = player();
        SportResource court = exclusiveCourt();
        ReservationRequest booking = request(court, LocalDate.now(WARSAW).plusDays(21), court.getOpeningTime(), 1);
        booking.setPaymentMethod(null);
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("payment method");
    }

    @Test
    void managerQueueExposesPartySizeAndPayment() {
        User player = player();
        SportResource tennis = tennisCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(22);
        ReservationRequest booking = request(tennis, date, tennis.getOpeningTime(), 1);
        booking.setPartySize(3);
        booking.setPaymentMethod(PaymentMethod.CARD_ON_SITE);
        var reservation = reservationService.create(player, booking);
        List<Reservation> queue = reservationService.findManagerQueue(date);
        Reservation shown = queue.stream()
                .filter(item -> item.getId().equals(reservation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(shown.getPartySize()).isEqualTo(3);
        assertThat(shown.getPaymentMethod()).isEqualTo(PaymentMethod.CARD_ON_SITE);
        assertThat(shown.getPartySizeLabel()).isEqualTo("3");
        assertThat(shown.getTotalAmount()).isNotNull();
    }

    @Test
    void partySizeLabelUsesPlusOnGroupMax() {
        SportResource tennis = tennisCourt();
        SportResource gym = gym();
        int max = tennis.getMaxPartySize();
        assertThat(max).isGreaterThan(1);
        assertThat(tennis.partySizeLabel(2)).isEqualTo("2");
        assertThat(tennis.partySizeLabel(3)).isEqualTo("3");
        assertThat(tennis.partySizeLabel(max)).isEqualTo(max + "+");
        assertThat(gym.partySizeLabel(1)).isEqualTo("1");
        assertThat(gym.requiresLessonPartySize()).isTrue();
        assertThat(gym.getLessonMinPartySize()).isEqualTo(2);
        assertThat(gym.getLessonMaxPartySize()).isEqualTo(gym.getCapacity());
        assertThat(gym.partySizeLabel(gym.getCapacity(), ReservationKind.LESSON)).isEqualTo(String.valueOf(gym.getCapacity()));
        User player = player();
        ReservationRequest booking = request(tennis, LocalDate.now(WARSAW).plusDays(23), tennis.getOpeningTime(), 1);
        booking.setPartySize(max);
        var reservation = reservationService.create(player, booking);
        assertThat(reservation.getPartySize()).isEqualTo(max);
        assertThat(reservation.getPartySizeLabel()).isEqualTo(max + "+");
    }

    @Test
    void weekendEveningTennisCostsMoreThanWeekdayMorning() {
        SportResource tennis = tennisCourt();
        LocalDate weekday = nextWeekday(LocalDate.now(WARSAW).plusDays(24));
        LocalDate weekend = nextSaturday(weekday);
        BigDecimal morning = pricingService.quote(tennis, weekday, LocalTime.of(10, 0), 1);
        BigDecimal evening = pricingService.quote(tennis, weekend, LocalTime.of(18, 0), 1);
        assertThat(evening).isGreaterThan(morning);
    }

    @Test
    void twoHourQuoteSumsDaytimeAndEveningHours() {
        SportResource tennis = tennisCourt();
        LocalDate weekday = nextWeekday(LocalDate.now(WARSAW).plusDays(25));
        BigDecimal daytime = pricingService.hourlyRate(tennis, weekday, LocalTime.of(16, 0));
        BigDecimal evening = pricingService.hourlyRate(tennis, weekday, LocalTime.of(17, 0));
        BigDecimal twoHours = pricingService.quote(tennis, weekday, LocalTime.of(16, 0), 2);
        assertThat(evening).isGreaterThan(daytime);
        assertThat(twoHours).isEqualByComparingTo(daytime.add(evening));
    }

    @Test
    void quoteMatchesStoredReservationAmount() {
        User player = player();
        SportResource tennis = tennisCourt();
        LocalDate weekday = nextWeekday(LocalDate.now(WARSAW).plusDays(26));
        LocalTime start = LocalTime.of(10, 0);
        BigDecimal quoted = pricingService.quote(tennis, weekday, start, 1);
        var reservation = reservationService.create(player, request(tennis, weekday, start, 1));
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(quoted);
        assertThat(reservation.getFormattedTotalAmount()).endsWith(" PLN");
    }

    @Test
    void gymIndividualDoesNotOccupyFullCapacity() {
        User player = player();
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(28);
        ReservationRequest booking = request(gym, date, gym.getOpeningTime(), 1);
        booking.setKind(ReservationKind.INDIVIDUAL);
        var reservation = reservationService.create(player, booking);
        assertThat(reservation.getOccupancyUnits()).isEqualTo(1);
        assertThat(reservation.getKind()).isEqualTo(ReservationKind.INDIVIDUAL);
        assertThat(reservation.getPartySize()).isEqualTo(1);
        assertThat(reservationRepository.countOverlapping(
                gym.getId(),
                ReservationStatus.occupying(),
                date.atTime(gym.getOpeningTime()),
                date.atTime(gym.getOpeningTime()).plusHours(1)
        )).isEqualTo(1);
        assertThat(gym.getCapacity()).isGreaterThan(1);
        ReservationRequest second = request(gym, date, gym.getOpeningTime(), 1);
        second.setKind(ReservationKind.INDIVIDUAL);
        reservationService.create(admin(), second);
        assertThat(reservationRepository.countOverlapping(
                gym.getId(),
                ReservationStatus.occupying(),
                date.atTime(gym.getOpeningTime()),
                date.atTime(gym.getOpeningTime()).plusHours(1)
        )).isEqualTo(2);
    }

    @Test
    void gymLessonOccupiesFullCapacityAndBlocksOverlap() {
        User player = player();
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(29);
        ReservationRequest lesson = request(gym, date, gym.getOpeningTime(), 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(2);
        var reservation = reservationService.create(player, lesson);
        assertThat(reservation.getOccupancyUnits()).isEqualTo(gym.getCapacity());
        assertThat(reservation.getKind()).isEqualTo(ReservationKind.LESSON);
        assertThat(reservation.getPartySize()).isEqualTo(2);
        assertThat(reservationRepository.countOverlapping(
                gym.getId(),
                ReservationStatus.occupying(),
                date.atTime(gym.getOpeningTime()),
                date.atTime(gym.getOpeningTime()).plusHours(1)
        )).isEqualTo(gym.getCapacity());
        ReservationRequest other = request(gym, date, gym.getOpeningTime(), 1);
        other.setKind(ReservationKind.INDIVIDUAL);
        assertThatThrownBy(() -> reservationService.create(admin(), other))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void lessonRejectedWhenIndividualAlreadyHoldsHour() {
        User player = player();
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(30);
        ReservationRequest individual = request(gym, date, gym.getOpeningTime(), 1);
        individual.setKind(ReservationKind.INDIVIDUAL);
        reservationService.create(player, individual);
        ReservationRequest lesson = request(gym, date, gym.getOpeningTime(), 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(6);
        assertThatThrownBy(() -> reservationService.create(admin(), lesson))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("people are already coming");
    }

    @Test
    void extrasAddPricePerPersonTimesPeople() {
        User player = player();
        SportResource tennis = tennisCourt();
        InventoryItem racket = inventoryItemRepository.findByResourceTypeAndEnabledTrueOrderByNameAsc(ResourceType.CHAPEL)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase("Floral tribute"))
                .findFirst()
                .orElseThrow();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(31));
        LocalTime start = LocalTime.of(10, 0);
        ReservationRequest booking = request(tennis, date, start, 1);
        booking.setPartySize(2);
        booking.setExtraIds(List.of(racket.getId()));
        BigDecimal court = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(), 2);
        BigDecimal quoted = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(racket), 2);
        assertThat(quoted).isEqualByComparingTo(court.add(racket.getPricePerPerson().multiply(BigDecimal.valueOf(2))));
        var reservation = reservationService.create(player, booking);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(quoted);
        assertThat(reservation.getExtrasSummary()).contains("Floral tribute ×2");
    }

    @Test
    void tennisPartySizeThreeShowsInQueueAndMultipliesExtras() {
        User player = player();
        SportResource tennis = tennisCourt();
        InventoryItem racket = inventoryItemRepository.findByResourceTypeAndEnabledTrueOrderByNameAsc(ResourceType.CHAPEL)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase("Floral tribute"))
                .findFirst()
                .orElseThrow();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(33));
        LocalTime start = LocalTime.of(10, 0);
        ReservationRequest booking = request(tennis, date, start, 1);
        booking.setPartySize(3);
        booking.setExtraIds(List.of(racket.getId()));
        BigDecimal court = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(), 3);
        BigDecimal quoted = pricingService.quote(tennis, date, start, 1, ReservationKind.STANDARD, List.of(racket), 3);
        assertThat(quoted).isEqualByComparingTo(court.add(racket.getPricePerPerson().multiply(BigDecimal.valueOf(3))));
        var reservation = reservationService.create(player, booking);
        assertThat(reservation.getPartySize()).isEqualTo(3);
        assertThat(reservation.getPartySizeLabel()).isEqualTo("3");
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(quoted);
        assertThat(reservation.getExtrasSummary()).contains("Floral tribute ×3");
        Reservation queued = reservationService.findManagerQueue(date).stream()
                .filter(item -> item.getId().equals(reservation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(queued.getPartySize()).isEqualTo(3);
        assertThat(queued.getPartySizeLabel()).isEqualTo("3");
        Reservation history = reservationService.findForUser(player).stream()
                .filter(item -> item.getId().equals(reservation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(history.getPartySize()).isEqualTo(3);
        assertThat(history.getPartySizeLabel()).isEqualTo("3");
    }

    @Test
    void gymLessonStoresAttendeesAndStillLocksFullCapacity() {
        User player = player();
        SportResource gym = gym();
        InventoryItem towel = inventoryItemRepository.findByResourceTypeAndEnabledTrueOrderByNameAsc(ResourceType.TRANSPORT)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase("Following car"))
                .findFirst()
                .orElseThrow();
        LocalDate date = nextWeekday(LocalDate.now(WARSAW).plusDays(34));
        LocalTime start = LocalTime.of(10, 0);
        ReservationRequest lesson = request(gym, date, start, 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(6);
        lesson.setExtraIds(List.of(towel.getId()));
        BigDecimal space = pricingService.quote(gym, date, start, 1, ReservationKind.LESSON, List.of(), 6);
        BigDecimal quoted = pricingService.quote(gym, date, start, 1, ReservationKind.LESSON, List.of(towel), 6);
        assertThat(quoted).isEqualByComparingTo(space.add(towel.getPricePerPerson().multiply(BigDecimal.valueOf(6))));
        var reservation = reservationService.create(player, lesson);
        assertThat(reservation.getPartySize()).isEqualTo(6);
        assertThat(reservation.getPartySizeLabel()).isEqualTo("6");
        assertThat(reservation.getOccupancyUnits()).isEqualTo(gym.getCapacity());
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(quoted);
        assertThat(reservation.getExtrasSummary()).contains("Following car ×6");
        assertThat(reservationRepository.countOverlapping(
                gym.getId(),
                ReservationStatus.occupying(),
                date.atTime(start),
                date.atTime(start).plusHours(1)
        )).isEqualTo(gym.getCapacity());
        ReservationRequest other = request(gym, date, start, 1);
        other.setKind(ReservationKind.INDIVIDUAL);
        assertThatThrownBy(() -> reservationService.create(admin(), other))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void gymLessonWithoutPartySizeFails() {
        User player = player();
        SportResource gym = gym();
        ReservationRequest lesson = request(gym, LocalDate.now(WARSAW).plusDays(35), gym.getOpeningTime(), 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(null);
        assertThatThrownBy(() -> reservationService.create(player, lesson))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("how many people");
    }

    @Test
    void gymLessonCapacityIsHardMaxWithoutPlus() {
        User player = player();
        SportResource gym = gymWithCapacity(8);
        ReservationRequest lesson = request(gym, LocalDate.now(WARSAW).plusDays(36), gym.getOpeningTime(), 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(8);
        var reservation = reservationService.create(player, lesson);
        assertThat(reservation.getPartySize()).isEqualTo(8);
        assertThat(reservation.getPartySizeLabel()).isEqualTo("8");
        assertThat(reservation.getOccupancyUnits()).isEqualTo(gym.getCapacity());
        ReservationRequest over = request(gym, LocalDate.now(WARSAW).plusDays(37), gym.getOpeningTime(), 1);
        over.setKind(ReservationKind.LESSON);
        over.setPartySize(21);
        assertThatThrownBy(() -> reservationService.create(admin(), over))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("between");
        ReservationRequest single = request(gym, LocalDate.now(WARSAW).plusDays(38), gym.getOpeningTime(), 1);
        single.setKind(ReservationKind.LESSON);
        single.setPartySize(1);
        assertThatThrownBy(() -> reservationService.create(admin(), single))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("between");
    }

    @Test
    void swimLessonMaxMatchesPoolCapacity() {
        User player = player();
        SportResource pool = pool();
        assertThat(pool.getCapacity()).isGreaterThan(1);
        assertThat(pool.getLessonMinPartySize()).isEqualTo(2);
        assertThat(pool.getLessonMaxPartySize()).isEqualTo(pool.getCapacity());
        ReservationRequest lesson = request(pool, LocalDate.now(WARSAW).plusDays(39), pool.getOpeningTime(), 1);
        lesson.setKind(ReservationKind.LESSON);
        lesson.setPartySize(pool.getCapacity());
        var reservation = reservationService.create(player, lesson);
        assertThat(reservation.getPartySize()).isEqualTo(pool.getCapacity());
        assertThat(reservation.getPartySizeLabel()).isEqualTo(String.valueOf(pool.getCapacity()));
        assertThat(reservation.getOccupancyUnits()).isEqualTo(pool.getCapacity());
        ReservationRequest over = request(pool, LocalDate.now(WARSAW).plusDays(40), pool.getOpeningTime(), 1);
        over.setKind(ReservationKind.LESSON);
        over.setPartySize(pool.getCapacity() + 1);
        assertThatThrownBy(() -> reservationService.create(admin(), over))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("between");
    }

    @Test
    void confirmedReservationCannotBeCancelled() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        var reservation = reservationService.create(
                admin,
                request(court, LocalDate.now(WARSAW).plusDays(16), court.getOpeningTime(), 1)
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> reservationService.cancel(admin, reservation.getId(), "CHANGE_OF_PLANS"))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Confirmed");
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void deleteEndedBeforeRemovesOldHistoryOnly() {
        User admin = admin();
        SportResource court = exclusiveCourt();
        LocalDateTime now = LocalDateTime.now(WARSAW);
        Reservation old = persistEnded(admin, court, now.minusMonths(2), now.minusMonths(2).plusHours(1));
        Reservation recent = persistEnded(admin, court, now.minusDays(10), now.minusDays(10).plusHours(1));
        Reservation upcoming = reservationService.create(
                admin,
                request(court, LocalDate.now(WARSAW).plusDays(17), court.getOpeningTime(), 1)
        );
        int removed = reservationService.deleteEndedBefore(now.minusMonths(1));
        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(reservationRepository.findById(old.getId())).isEmpty();
        assertThat(reservationRepository.findById(recent.getId())).isPresent();
        assertThat(reservationRepository.findById(upcoming.getId())).isPresent();
    }

    @Test
    void managerCanConfirmPendingReservation() {
        User player = player();
        User manager = userRepository.findByUsernameIgnoreCase("manager").orElseThrow();
        SportResource court = exclusiveCourt();
        var reservation = reservationService.create(
                player,
                request(court, LocalDate.now(WARSAW).plusDays(9), court.getOpeningTime(), 1)
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        reservationService.confirm(manager, reservation.getId());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void playerCannotConfirmOwnReservation() {
        User player = player();
        SportResource court = exclusiveCourt();
        var reservation = reservationService.create(
                player,
                request(court, LocalDate.now(WARSAW).plusDays(10), court.getOpeningTime(), 1)
        );
        assertThatThrownBy(() -> reservationService.confirm(player, reservation.getId()))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("manager");
    }

    @Test
    void bookingSavesPhoneWhenProfileHasNone() {
        User player = playerWithoutPhone();
        SportResource court = exclusiveCourt();
        ReservationRequest booking = request(court, LocalDate.now(WARSAW).plusDays(11), court.getOpeningTime(), 1);
        booking.setPhone("+48 555 010 099");
        reservationService.create(player, booking);
        assertThat(userRepository.findByUsernameIgnoreCase(player.getUsername()).orElseThrow().getPhone())
                .isEqualTo("+48 555 010 099");
    }

    private User admin() {
        return userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
    }

    private User player() {
        return userRepository.findByUsernameIgnoreCase("court_player").orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername("court_player");
            request.setEmail("court.player@example.com");
            request.setFullName("Court Player");
            request.setPhone("+48 555 010 010");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            return userService.register(request);
        });
    }

    private User playerWithoutPhone() {
        return userRepository.findByUsernameIgnoreCase("nophone_player").orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername("nophone_player");
            request.setEmail("nophone.player@example.com");
            request.setFullName("No Phone Player");
            request.setPassword("Password1");
            request.setConfirmPassword("Password1");
            return userService.register(request);
        });
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

    private SportResource gymWithCapacity(int capacity) {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.TRANSPORT && resource.isEnabled() && resource.getCapacity() == capacity)
                .findFirst()
                .orElseThrow();
    }

    private SportResource pool() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.VIEWING && resource.isEnabled() && resource.getCapacity() > 1)
                .findFirst()
                .orElseThrow();
    }

    private Reservation persistEnded(User user, SportResource court, LocalDateTime startAt, LocalDateTime endAt) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setResource(court);
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPartySize(1);
        reservation.setPaymentMethod(PaymentMethod.CASH);
        reservation.setTotalAmount(BigDecimal.ZERO.setScale(2));
        reservation.setKind(ReservationKind.STANDARD);
        reservation.setOccupancyUnits(1);
        return reservationRepository.saveAndFlush(reservation);
    }

    private ReservationRequest request(SportResource resource, LocalDate date, LocalTime startTime) {
        return request(resource, date, startTime, 1);
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
