package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.OccupancyCell;
import com.example.hackathoncodaro2026.dto.OccupancyGrid;
import com.example.hackathoncodaro2026.dto.OccupancyRow;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Facility;
import com.example.hackathoncodaro2026.model.InventoryItem;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.repository.FacilityRepository;
import com.example.hackathoncodaro2026.repository.InventoryItemRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.OccupancyService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.ResourceService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class FacilityOccupancyAndReservationRuleTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PricingService pricingService;

    @Test
    void seededResourcesHaveWarsawAddressesHoursAndCapacity() {
        List<SportResource> resources = sportResourceRepository.findAll();
        assertThat(resources).isNotEmpty();
        assertThat(facilityRepository.findAll()).isNotEmpty();
        for (SportResource resource : resources) {
            assertThat(resource.getAddress()).isNotNull();
            assertThat(resource.getAddress().getStreet()).isNotBlank();
            assertThat(resource.getAddress().getBuildingNumber()).isNotBlank();
            assertThat(resource.getAddress().getPostalCode()).matches("\\d{2}-\\d{3}");
            assertThat(resource.getAddress().getCity()).isEqualTo("Warszawa");
            assertThat(resource.getAddress().getDistrict()).isNotBlank();
            assertThat(resource.getOpeningTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(resource.getClosingTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(resource.getCapacity()).isGreaterThanOrEqualTo(1);
            assertThat(resource.getSlotDurationMinutes()).isEqualTo(60);
        }
        for (Facility facility : facilityRepository.findAll()) {
            assertThat(facility.getAddress().getCity()).isEqualTo("Warszawa");
            assertThat(facility.isEnabled()).isTrue();
        }
        assertThat(resources.stream().map(SportResource::getType).distinct())
                .contains(
                        ResourceType.CHAPEL,
                        ResourceType.RECEPTION,
                        ResourceType.CREMATION,
                        ResourceType.BURIAL,
                        ResourceType.VIEWING,
                        ResourceType.TRANSPORT,
                        ResourceType.REPATRIATION
                );
        SportResource chapel = tennisCourt();
        assertThat(chapel.getMinPartySize()).isEqualTo(ResourceType.CHAPEL.getMinPartySize());
        assertThat(chapel.getMaxPartySize()).isEqualTo(ResourceType.CHAPEL.getMaxPartySize());
        assertThat(chapel.requiresPartySize()).isTrue();
        SportResource gym = gym();
        assertThat(gym.requiresBookingMode()).isTrue();
        assertThat(gym.getMinPartySize()).isEqualTo(1);
        assertThat(gym.getMaxPartySize()).isEqualTo(1);
    }

    @Test
    void disabledResourceAndFacilityCannotBeBooked() {
        User player = player("disabled_book", "disabled.book@example.com");
        SportResource court = exclusiveCourt();
        court.setEnabled(false);
        sportResourceRepository.saveAndFlush(court);
        assertThat(resourceService.findEnabledWithFacility(court.getId())).isEmpty();
        assertThatThrownBy(() -> reservationService.create(player, booking(court, LocalDate.now(WARSAW).plusDays(42))))
                .isInstanceOf(ReservationException.class);
        court.setEnabled(true);
        sportResourceRepository.saveAndFlush(court);
        Facility facility = court.getFacility();
        facility.setEnabled(false);
        facilityRepository.saveAndFlush(facility);
        assertThatThrownBy(() -> reservationService.create(player, booking(court, LocalDate.now(WARSAW).plusDays(43))))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("not open");
    }

    @Test
    void durationBoundsSlotAlignmentAndAdjacentIntervals() {
        User player = player("slot_rules", "slot.rules@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(44);
        ReservationRequest tooLong = booking(court, date);
        tooLong.setDurationHours(5);
        assertThatThrownBy(() -> reservationService.create(player, tooLong))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Duration");
        ReservationRequest unaligned = booking(court, date);
        unaligned.setStartTime(court.getOpeningTime().plusMinutes(15));
        assertThatThrownBy(() -> reservationService.create(player, unaligned))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("slot");
        Reservation first = reservationService.create(player, booking(court, date, court.getOpeningTime()));
        Reservation second = reservationService.create(
                player,
                booking(court, date, court.getOpeningTime().plusHours(1))
        );
        assertThat(first.getEndAt()).isEqualTo(second.getStartAt());
        assertThat(reservationRepository.countOverlapping(
                court.getId(),
                ReservationStatus.occupying(),
                date.atTime(court.getOpeningTime()),
                date.atTime(court.getOpeningTime()).plusHours(1)
        )).isEqualTo(1);
        assertThat(reservationRepository.countOverlapping(
                court.getId(),
                ReservationStatus.occupying(),
                date.atTime(court.getOpeningTime().plusHours(1)),
                date.atTime(court.getOpeningTime().plusHours(2))
        )).isEqualTo(1);
    }

    @Test
    void cancelledBookingDoesNotOccupyAndQueueHidesIt() {
        User player = player("cancel_occ", "cancel.occ@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(45);
        Reservation reservation = reservationService.create(player, booking(court, date, court.getOpeningTime()));
        reservationService.cancel(player, reservation.getId(), "CHANGE_OF_PLANS");
        assertThat(reservationRepository.countOverlapping(
                court.getId(),
                ReservationStatus.occupying(),
                date.atTime(court.getOpeningTime()),
                date.atTime(court.getOpeningTime()).plusHours(1)
        )).isEqualTo(0);
        OccupancyCell cell = cell(occupancyService.gridFor(date, null), court.getId(), court.getOpeningTime());
        assertThat(cell.getBooked()).isEqualTo(0);
        assertThat(cell.getLevel()).isEqualTo("free");
        assertThat(cell.isBookable()).isTrue();
        assertThat(reservationService.findManagerQueue(date)).extracting(Reservation::getId)
                .doesNotContain(reservation.getId());
    }

    @Test
    void occupancyFiltersByFacilityAndMarksFullLowAndClosed() {
        User player = player("occ_grid", "occ.grid@example.com");
        SportResource court = exclusiveCourt();
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(46);
        reservationService.create(player, booking(court, date, court.getOpeningTime()));
        OccupancyGrid all = occupancyService.gridFor(date, null);
        OccupancyGrid filtered = occupancyService.gridFor(date, court.getFacility().getId());
        assertThat(filtered.getRows()).isNotEmpty();
        assertThat(filtered.getRows()).allMatch(row -> all.getRows().stream()
                .anyMatch(item -> item.getResourceId().equals(row.getResourceId())));
        assertThat(filtered.getRows().stream().map(OccupancyRow::getFacilityName).distinct())
                .containsExactly(court.getFacility().getName());
        OccupancyCell full = cell(all, court.getId(), court.getOpeningTime());
        assertThat(full.getLevel()).isEqualTo("full");
        assertThat(full.isBookable()).isFalse();
        assertThat(full.getRemaining()).isEqualTo(0);
        OccupancyCell gymCell = cell(all, gym.getId(), gym.getOpeningTime());
        assertThat(gymCell.getLevel()).isEqualTo("free");
        assertThat(gymCell.isBookable()).isTrue();
        OccupancyCell closed = all.getRows().stream()
                .filter(row -> row.getResourceId().equals(court.getId()))
                .flatMap(row -> row.getCells().stream())
                .filter(item -> "closed".equals(item.getLevel()))
                .findFirst()
                .orElse(null);
        if (closed != null) {
            assertThat(closed.isBookable()).isFalse();
        }
    }

    @Test
    void gymPartialLevelAfterSeveralIndividualBookings() {
        User player = player("occ_low", "occ.low@example.com");
        SportResource gym = gym();
        LocalDate date = LocalDate.now(WARSAW).plusDays(47);
        ReservationRequest booking = booking(gym, date, gym.getOpeningTime());
        booking.setKind(ReservationKind.INDIVIDUAL);
        reservationService.create(player, booking);
        OccupancyCell cell = cell(occupancyService.gridFor(date, null), gym.getId(), gym.getOpeningTime());
        assertThat(cell.getBooked()).isEqualTo(1);
        assertThat(cell.getCapacity()).isEqualTo(gym.getCapacity());
        assertThat(cell.getLevel()).isEqualTo("low");
        assertThat(cell.isBookable()).isTrue();
    }

    @Test
    void tennisLessonKindIsStoredAsStandardPlaceBooking() {
        User player = player("lesson_court", "lesson.court@example.com");
        SportResource tennis = tennisCourt();
        ReservationRequest booking = booking(tennis, LocalDate.now(WARSAW).plusDays(48));
        booking.setKind(ReservationKind.LESSON);
        Reservation reservation = reservationService.create(player, booking);
        assertThat(reservation.getKind()).isEqualTo(ReservationKind.STANDARD);
        assertThat(reservation.getOccupancyUnits()).isEqualTo(1);
    }

    @Test
    void extrasDeduplicateIdsAndIndividualUsesOneHeadcount() {
        User player = player("extra_once", "extra.once@example.com");
        SportResource gym = gym();
        InventoryItem towel = inventoryItemRepository.findByResourceTypeAndEnabledTrueOrderByNameAsc(ResourceType.TRANSPORT)
                .stream()
                .filter(item -> item.getName().equalsIgnoreCase("Following car"))
                .findFirst()
                .orElseThrow();
        LocalDate date = LocalDate.now(WARSAW).plusDays(49);
        ReservationRequest booking = booking(gym, date, gym.getOpeningTime());
        booking.setKind(ReservationKind.INDIVIDUAL);
        booking.setExtraIds(List.of(towel.getId(), towel.getId()));
        BigDecimal space = pricingService.quote(gym, date, gym.getOpeningTime(), 1, ReservationKind.INDIVIDUAL, List.of(), 1);
        BigDecimal quoted = pricingService.quote(
                gym,
                date,
                gym.getOpeningTime(),
                1,
                ReservationKind.INDIVIDUAL,
                List.of(towel),
                1
        );
        assertThat(quoted).isEqualByComparingTo(space.add(towel.getPricePerPerson()));
        Reservation reservation = reservationService.create(player, booking);
        assertThat(reservation.getPartySize()).isEqualTo(1);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(quoted);
        assertThat(reservation.getExtras().stream().filter(extra -> extra.getItem() != null).count()).isEqualTo(1);
    }

    @Test
    void cancelRequiresOwnedPendingReasonAndOtherNoteLimit() {
        User owner = player("cancel_owner", "cancel.owner@example.com");
        User other = player("cancel_other", "cancel.other@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(50);
        Reservation reservation = reservationService.create(owner, booking(court, date, court.getOpeningTime()));
        assertThatThrownBy(() -> reservationService.cancel(owner, reservation.getId()))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> reservationService.cancel(owner, reservation.getId(), "not-a-reason"))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> reservationService.cancel(other, reservation.getId(), "CHANGE_OF_PLANS"))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("own reservation");
        assertThatThrownBy(() -> reservationService.cancel(owner, reservation.getId(), "OTHER", "x".repeat(401)))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("400");
        reservationService.cancel(owner, reservation.getId(), "OTHER", "Moved house");
        Reservation stored = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).startsWith("Other");
        assertThat(stored.getCancellationReason()).contains("Moved house");
    }

    @Test
    void historyIsScopedAndStaffQueueExposesSafeContactFields() {
        User owner = player("hist_owner", "hist.owner@example.com");
        User other = player("hist_other", "hist.other@example.com");
        SportResource court = exclusiveCourt();
        LocalDate date = LocalDate.now(WARSAW).plusDays(52);
        Reservation mine = reservationService.create(owner, booking(court, date, court.getOpeningTime()));
        Reservation theirs = reservationService.create(other, booking(court, date, court.getOpeningTime().plusHours(2)));
        assertThat(reservationService.findForUser(owner)).extracting(Reservation::getId)
                .contains(mine.getId())
                .doesNotContain(theirs.getId());
        assertThat(reservationService.findAll()).extracting(Reservation::getId)
                .contains(mine.getId(), theirs.getId());
        Reservation queued = reservationService.findManagerQueue(date).stream()
                .filter(item -> item.getId().equals(mine.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(queued.getUser().getEmail()).isEqualTo("hist.owner@example.com");
        assertThat(queued.getUser().getPhone()).isEqualTo("+48 555 010 116");
        assertThat(queued.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(queued.getTotalAmount()).isNotNull();
        assertThat(reservationService.findManagerQueue(null)).extracting(Reservation::getId)
                .doesNotContain(mine.getId());
    }

    @Test
    void repeatConfirmIsRejected() {
        User player = player("confirm_twice", "confirm.twice@example.com");
        User manager = userRepository.findByUsernameIgnoreCase("manager").orElseThrow();
        SportResource court = exclusiveCourt();
        Reservation reservation = reservationService.create(
                player,
                booking(court, LocalDate.now(WARSAW).plusDays(53), court.getOpeningTime())
        );
        reservationService.confirm(manager, reservation.getId());
        assertThatThrownBy(() -> reservationService.confirm(manager, reservation.getId()))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void receptionCarriesMournersAndTransportStaysIndividual() {
        SportResource football = sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getType() == ResourceType.RECEPTION && resource.isEnabled())
                .findFirst()
                .orElseThrow();
        assertThat(football.getMinPartySize()).isEqualTo(ResourceType.RECEPTION.getMinPartySize());
        assertThat(football.getMaxPartySize()).isEqualTo(ResourceType.RECEPTION.getMaxPartySize());
        User player = player("party_fb", "party.fb@example.com");
        ReservationRequest tooSmall = booking(football, LocalDate.now(WARSAW).plusDays(54));
        tooSmall.setPartySize(1);
        assertThatThrownBy(() -> reservationService.create(player, tooSmall))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("between");
        SportResource gym = gym();
        ReservationRequest individual = booking(gym, LocalDate.now(WARSAW).plusDays(55));
        individual.setKind(ReservationKind.INDIVIDUAL);
        individual.setPartySize(null);
        assertThat(reservationService.create(player, individual).getPartySize()).isEqualTo(1);
    }

    private OccupancyCell cell(OccupancyGrid grid, Long resourceId, LocalTime start) {
        OccupancyRow row = grid.getRows().stream()
                .filter(item -> item.getResourceId().equals(resourceId))
                .findFirst()
                .orElseThrow();
        return row.getCells().stream()
                .filter(item -> item.getStart().equals(start))
                .findFirst()
                .orElseThrow();
    }

    private User player(String username, String email) {
        return userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setFullName("Player " + username);
            request.setPhone("+48 555 010 116");
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

    private ReservationRequest booking(SportResource resource, LocalDate date) {
        return booking(resource, date, resource.getOpeningTime());
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
}
