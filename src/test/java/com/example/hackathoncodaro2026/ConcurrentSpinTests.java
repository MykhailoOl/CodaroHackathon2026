package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.model.Address;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.model.enums.VenueType;
import com.example.hackathoncodaro2026.repository.FuneralHomeRepository;
import com.example.hackathoncodaro2026.repository.ReservationExtraRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.service.DateAssignmentService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentSpinTests {

    @Autowired
    private FuneralHomeRepository funeralHomeRepository;

    @Autowired
    private ServiceVenueRepository serviceVenueRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationExtraRepository reservationExtraRepository;

    @Autowired
    private DateAssignmentService dateAssignmentService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserService userService;

    @AfterEach
    void cleanup() {
        reservationExtraRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    void lastChapelSlotAllowsExactlyOneWinner() throws Exception {
        runLastSlot("spin_left", "spin_right", "1");
    }

    @Test
    void lastHallSlotAllowsExactlyOneWinner() throws Exception {
        runLastSlot("hall_left", "hall_right", "2");
    }

    private void runLastSlot(String leftName, String rightName, String building) throws Exception {
        FuneralHome home = funeralHomeRepository.findByEnabledTrueOrderByNameAsc().getFirst();
        ServiceVenue venue = new ServiceVenue();
        venue.setFuneralHome(home);
        venue.setName("Single Opening " + building);
        venue.setType(VenueType.CHAPEL);
        venue.setAddress(new Address("Testowa", building, "00-001", "Śródmieście"));
        venue.setMaxAttendees(20);
        venue.setOpeningTime(LocalTime.of(10, 0));
        venue.setClosingTime(LocalTime.of(11, 30));
        venue.setSlotDurationMinutes(30);
        venue.setEnabled(true);
        venue = serviceVenueRepository.saveAndFlush(venue);
        final Long venueId = venue.getId();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
        List<LocalDateTime> starts = dateAssignmentService.availableStarts(venue, FuneralPackage.ESSENTIAL).stream()
                .filter(start -> {
                    LocalDate day = start.toLocalDate();
                    return !day.isBefore(today) && !day.isAfter(today.plusDays(3));
                })
                .toList();
        assertThat(starts).isNotEmpty();
        User filler = userService.findByUsername("admin").orElseThrow();
        for (int i = 0; i < starts.size() - 1; i++) {
            occupy(venue, filler, starts.get(i));
        }
        final String leftUser = family(leftName).getUsername();
        final String rightUser = family(rightName).getUsername();
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>("");
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> spin(leftUser, venueId, barrier, wins, losses, done, error));
            pool.submit(() -> spin(rightUser, venueId, barrier, wins, losses, done, error));
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(wins.get()).as("wins=%s losses=%s error=%s", wins.get(), losses.get(), error.get()).isEqualTo(1);
        assertThat(losses.get()).isEqualTo(1);
        LocalDateTime last = starts.get(starts.size() - 1);
        assertThat(reservationRepository.countOverlapping(
                venueId,
                ReservationStatus.occupying(),
                last,
                last.plusMinutes(FuneralPackage.ESSENTIAL.getDurationMinutes())
        )).isEqualTo(1);
    }

    private void spin(
            String username,
            Long venueId,
            CyclicBarrier barrier,
            AtomicInteger wins,
            AtomicInteger losses,
            CountDownLatch done,
            java.util.concurrent.atomic.AtomicReference<String> error
    ) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
            User user = userService.findByUsername(username).orElseThrow();
            ServiceVenue venue = serviceVenueRepository.findById(venueId).orElseThrow();
            reservationService.create(user, request(venue, user));
            wins.incrementAndGet();
        } catch (Exception ex) {
            losses.incrementAndGet();
            error.set(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            done.countDown();
        }
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

    private User family(String username) {
        return userService.findByUsername(username).orElseGet(() -> {
            RegistrationRequest registration = new RegistrationRequest();
            registration.setUsername(username);
            registration.setEmail(username + "@example.com");
            registration.setFullName(username);
            registration.setPassword("Password1");
            registration.setConfirmPassword("Password1");
            registration.setPhone("+48 555 010 222");
            return userService.register(registration);
        });
    }

    private ArrangementRequest request(ServiceVenue venue, User user) {
        ArrangementRequest request = new ArrangementRequest();
        request.setVenueId(venue.getId());
        request.setServiceType(ServiceType.MEMORIAL_SERVICE);
        request.setFuneralPackage(FuneralPackage.ESSENTIAL);
        request.setDeceasedFullName("Concurrent Person");
        request.setDateOfDeath(LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).minusDays(1));
        request.setAttendees(1);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPhone(user.getPhone());
        return request;
    }
}
