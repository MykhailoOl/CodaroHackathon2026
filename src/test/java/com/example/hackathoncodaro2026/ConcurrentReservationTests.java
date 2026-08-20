package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.CoachOfferingRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.CoachOfferingRepository;
import com.example.hackathoncodaro2026.repository.CoachRatingRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.CoachOfferingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentReservationTests {

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
    private CoachOfferingService coachOfferingService;

    @Autowired
    private CoachOfferingRepository coachOfferingRepository;

    @Autowired
    private CoachRatingRepository coachRatingRepository;

    @Test
    void onlyOneConcurrentBookingWinsLastCapacitySlot() throws Exception {
        User first = ensureUser("racer_one", "racer.one@example.com", "Racer One");
        User second = ensureUser("racer_two", "racer.two@example.com", "Racer Two");
        SportResource court = sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getCapacity() == 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
        LocalDate date = LocalDate.now(WARSAW).plusDays(14);
        LocalTime start = court.getOpeningTime().plusHours(3);
        Long resourceId = court.getId();
        Integer partySize = court.requiresPartySize() ? court.getMinPartySize() : null;

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(bookTask(first.getUsername(), resourceId, date, start, partySize, ReservationKind.STANDARD, null, null, ready, gate, done, successes, failures, errors));
        pool.submit(bookTask(second.getUsername(), resourceId, date, start, partySize, ReservationKind.STANDARD, null, null, ready, gate, done, successes, failures, errors));

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        gate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(successes.get())
                .withFailMessage("expected one success, failures=%s errors=%s", failures.get(), errors)
                .isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(reservationRepository.countOverlapping(
                resourceId,
                ReservationStatus.occupying(),
                date.atTime(start),
                date.atTime(start).plusHours(1)
        )).isEqualTo(1);
    }

    @Test
    void onlyOneWinsWhenLessonAndIndividualRaceGym() throws Exception {
        User first = ensureUser("racer_three", "racer.three@example.com", "Racer Three");
        User second = ensureUser("racer_four", "racer.four@example.com", "Racer Four");
        SportResource gym = sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.TRANSPORT && resource.getCapacity() > 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
        LocalDate date = LocalDate.now(WARSAW).plusDays(32);
        LocalTime start = gym.getOpeningTime().plusHours(4);
        Long resourceId = gym.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(bookTask(first.getUsername(), resourceId, date, start, null, ReservationKind.INDIVIDUAL, null, null, ready, gate, done, successes, failures, errors));
        pool.submit(bookTask(second.getUsername(), resourceId, date, start, gym.getLessonMinPartySize(), ReservationKind.LESSON, null, null, ready, gate, done, successes, failures, errors));

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        gate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(successes.get())
                .withFailMessage("expected one success, failures=%s errors=%s", failures.get(), errors)
                .isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        long occupied = reservationRepository.countOverlapping(
                resourceId,
                ReservationStatus.occupying(),
                date.atTime(start),
                date.atTime(start).plusHours(1)
        );
        assertThat(occupied == 1L || occupied == gym.getCapacity()).isTrue();
    }

    @Test
    void onlyOneWinsWhenSameCoachRacesTwoResources() throws Exception {
        User first = ensureUser("coach_racer_a", "coach.racer.a@example.com", "Coach Racer A");
        User second = ensureUser("coach_racer_b", "coach.racer.b@example.com", "Coach Racer B");
        List<SportResource> courts = sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.CHAPEL && resource.isEnabled() && resource.getCapacity() == 1)
                .limit(2)
                .toList();
        assertThat(courts).hasSize(2);
        SportResource firstCourt = courts.get(0);
        SportResource secondCourt = courts.get(1);
        LocalDate date = LocalDate.now(WARSAW).plusDays(51);
        LocalTime start = firstCourt.getOpeningTime().plusHours(2);
        userRepository.findByUsernameIgnoreCase("race_lock_coach").ifPresent(this::removeCommittedCoach);
        User coach = createCoach("race_lock_coach", "race.lock.coach@example.com");
        try {
            saveOffering(coach, ResourceType.CHAPEL, Set.of("CATHOLIC"), "80.00");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger failures = new AtomicInteger();
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            pool.submit(bookTask(
                    first.getUsername(),
                    firstCourt.getId(),
                    date,
                    start,
                    firstCourt.getMinPartySize(),
                    ReservationKind.STANDARD,
                    coach.getId(),
                    "CATHOLIC",
                    ready,
                    gate,
                    done,
                    successes,
                    failures,
                    errors
            ));
            pool.submit(bookTask(
                    second.getUsername(),
                    secondCourt.getId(),
                    date,
                    start,
                    secondCourt.getMinPartySize(),
                    ReservationKind.STANDARD,
                    coach.getId(),
                    "CATHOLIC",
                    ready,
                    gate,
                    done,
                    successes,
                    failures,
                    errors
            ));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(successes.get())
                    .withFailMessage("expected one success, failures=%s errors=%s", failures.get(), errors)
                    .isEqualTo(1);
            assertThat(failures.get()).isEqualTo(1);
            assertThat(errors.stream().anyMatch(message -> message.contains("no longer available"))).isTrue();
            assertThat(reservationRepository.countCoachOverlapping(
                    coach.getId(),
                    ReservationStatus.occupying(),
                    date.atTime(start),
                    date.atTime(start).plusHours(1)
            )).isEqualTo(1);
        } finally {
            removeCommittedCoach(coach);
        }
        assertThat(userRepository.existsByUsernameIgnoreCase("race_lock_coach")).isFalse();
        assertThat(userRepository.existsByRole(Role.COACH)).isFalse();
    }

    private Runnable bookTask(
            String username,
            Long resourceId,
            LocalDate date,
            LocalTime start,
            Integer partySize,
            ReservationKind kind,
            Long coachId,
            String skillLevel,
            CountDownLatch ready,
            CountDownLatch gate,
            CountDownLatch done,
            AtomicInteger successes,
            AtomicInteger failures,
            List<String> errors
    ) {
        return () -> {
            ready.countDown();
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) {
                    failures.incrementAndGet();
                    errors.add("start latch timed out");
                    return;
                }
                User user = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
                ReservationRequest request = new ReservationRequest();
                request.setResourceId(resourceId);
                request.setDate(date);
                request.setStartTime(start);
                request.setDurationHours(1);
                request.setPaymentMethod(PaymentMethod.CASH);
                request.setPartySize(partySize);
                request.setKind(kind);
                request.setCoachId(coachId);
                request.setSkillLevel(skillLevel);
                reservationService.create(user, request);
                successes.incrementAndGet();
            } catch (ReservationException ex) {
                failures.incrementAndGet();
                errors.add(ex.getMessage());
            } catch (Exception ex) {
                failures.incrementAndGet();
                errors.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                done.countDown();
            }
        };
    }

    private User ensureUser(String username, String email, String fullName) {
        return userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setFullName(fullName);
            request.setPhone("+48 555 010 020");
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

    private void removeCommittedCoach(User coach) {
        if (coach == null || coach.getId() == null) {
            return;
        }
        List<Reservation> booked = reservationRepository.findByCoachIdWithDetails(coach.getId());
        List<Long> reservationIds = booked.stream().map(Reservation::getId).toList();
        if (!reservationIds.isEmpty()) {
            coachRatingRepository.deleteAll(coachRatingRepository.findByReservation_IdIn(reservationIds));
        }
        reservationRepository.deleteAll(booked);
        coachOfferingRepository.deleteAll(coachOfferingRepository.findByCoach_IdOrderBySportTypeAsc(coach.getId()));
        userRepository.delete(coach);
        userRepository.flush();
    }
}
