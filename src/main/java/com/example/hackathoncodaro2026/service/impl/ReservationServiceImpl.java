package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementPreview;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.PriceQuote;
import com.example.hackathoncodaro2026.dto.ReservationUpdateResult;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ReservationExtra;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.CancellationReason;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.NotificationType;
import com.example.hackathoncodaro2026.model.enums.PricingMode;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.repository.ArrangementExtraRepository;
import com.example.hackathoncodaro2026.repository.ReservationExtraRepository;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.DateAssignmentService;
import com.example.hackathoncodaro2026.service.NotificationService;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9\\s().-]{7,20}$");
    private static final String NOTICE = "Dates stay open for others until a time is assigned. Availability is confirmed when a time is assigned.";

    private final ServiceVenueRepository serviceVenueRepository;
    private final ReservationRepository reservationRepository;
    private final ArrangementExtraRepository arrangementExtraRepository;
    private final ReservationExtraRepository reservationExtraRepository;
    private final DateAssignmentService dateAssignmentService;
    private final PricingService pricingService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ReservationServiceImpl(
            ServiceVenueRepository serviceVenueRepository,
            ReservationRepository reservationRepository,
            ArrangementExtraRepository arrangementExtraRepository,
            ReservationExtraRepository reservationExtraRepository,
            DateAssignmentService dateAssignmentService,
            PricingService pricingService,
            UserService userService,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this.serviceVenueRepository = serviceVenueRepository;
        this.reservationRepository = reservationRepository;
        this.arrangementExtraRepository = arrangementExtraRepository;
        this.reservationExtraRepository = reservationExtraRepository;
        this.dateAssignmentService = dateAssignmentService;
        this.pricingService = pricingService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Override
    public ArrangementPreview preview(User user, ArrangementRequest request) {
        Prepared prepared = prepare(user, request, true);
        List<LocalDate> dates = dateAssignmentService.previewDates(prepared.venue(), prepared.funeralPackage());
        if (dates.isEmpty()) {
            throw fail(user, "NO_SLOTS", "No ceremony times are free in the planning window. Try another venue or package.");
        }
        return new ArrangementPreview(
                dates,
                prepared.amount(),
                "PLN",
                expectedStatus(user),
                NOTICE
        );
    }

    @Override
    public PriceQuote quote(User user, ArrangementRequest request) {
        Prepared prepared = prepare(user, request, false);
        return new PriceQuote(prepared.amount(), "PLN");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArrangementCreateResponse spin(User user, ArrangementRequest request) {
        Reservation saved = create(user, request);
        return toCreateResponse(saved);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(User user, ArrangementRequest request) {
        String token = blankToNull(request.getSubmissionToken());
        if (token != null) {
            Optional<Reservation> existing = reservationRepository.findBySubmissionToken(token);
            if (existing.isPresent()) {
                return replayExisting(user, existing.get());
            }
        }
        return persistArrangement(user, request, token);
    }

    private Reservation persistArrangement(User user, ArrangementRequest request, String token) {
        ServiceVenue venue;
        try {
            venue = serviceVenueRepository.lockById(request.getVenueId())
                    .orElseThrow(() -> fail(user, "VENUE_NOT_FOUND", "venueId", "That venue could not be found"));
        } catch (TransientDataAccessException ex) {
            throw fail(user, "LOCK_TIMEOUT", "This time was just assigned, spin again");
        }
        Prepared prepared = prepare(user, request, true, venue);
        LocalDateTime startAt = dateAssignmentService.chooseStart(venue, prepared.funeralPackage());
        if (startAt == null) {
            throw fail(user, "NO_SLOTS", "No ceremony times are free in the planning window. Spin again after choosing another venue.");
        }
        LocalDateTime endAt = startAt.plusMinutes(prepared.funeralPackage().getDurationMinutes());
        if (reservationRepository.countOverlapping(venue.getId(), ReservationStatus.occupying(), startAt, endAt) > 0) {
            throw fail(user, "STALE_SLOT", "That time was just assigned, spin again");
        }
        applyPhone(user, request);
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setVenue(venue);
        reservation.setServiceType(prepared.serviceType());
        reservation.setFuneralPackage(prepared.funeralPackage());
        reservation.setDeceasedFullName(request.getDeceasedFullName().trim());
        reservation.setDateOfBirth(request.getDateOfBirth());
        reservation.setDateOfDeath(request.getDateOfDeath());
        reservation.setAttendees(prepared.attendees());
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(isStaff(user) ? ReservationStatus.CONFIRMED : ReservationStatus.PENDING);
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setTotalAmount(prepared.amount());
        reservation.setNote(blankToNull(request.getNote()));
        reservation.setSubmissionToken(token);
        attachExtras(reservation, prepared.extras(), prepared.attendees());
        try {
            Reservation saved = reservationRepository.saveAndFlush(reservation);
            auditCreated(saved, user, request, dateAssignmentService.availableStarts(venue, prepared.funeralPackage()).size());
            notify(
                    saved,
                    saved.getStatus() == ReservationStatus.CONFIRMED
                            ? NotificationType.RESERVATION_CONFIRMED
                            : NotificationType.RESERVATION_CREATED,
                    saved.getStatus() == ReservationStatus.CONFIRMED ? "Arrangement confirmed" : "Arrangement received",
                    saved.getStatus() == ReservationStatus.CONFIRMED
                            ? "Your ceremony time has been confirmed."
                            : "Your arrangement is pending review. A manager will confirm it."
            );
            return saved;
        } catch (DataIntegrityViolationException ex) {
            if (token == null) {
                throw ex;
            }
            throw new ReservationException("SUBMISSION", "submissionToken", "That confirmation is already recorded.");
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationUpdateResult update(User actor, Long reservationId, ArrangementRequest request) {
        Reservation reservation = reservationRepository.lockById(reservationId)
                .orElseThrow(() -> fail(actor, "NOT_FOUND", "That arrangement could not be found"));
        if (!canEdit(actor, reservation)) {
            throw fail(actor, "EDIT_FORBIDDEN", "Only a pending arrangement can be updated");
        }
        if (request.getVenueId() != null && !request.getVenueId().equals(reservation.getVenue().getId())) {
            throw fail(actor, "VENUE_FIXED", "venueId", "The venue cannot be changed after a time is assigned");
        }
        if (request.getFuneralPackage() != null && request.getFuneralPackage() != reservation.getFuneralPackage()) {
            throw fail(actor, "PACKAGE_FIXED", "funeralPackage", "The package cannot be changed after a time is assigned");
        }
        Prepared prepared = prepare(actor, request, true, reservation.getVenue());
        BigDecimal previous = reservation.getTotalAmount();
        reservation.setServiceType(prepared.serviceType());
        reservation.setDeceasedFullName(request.getDeceasedFullName().trim());
        reservation.setDateOfBirth(request.getDateOfBirth());
        reservation.setDateOfDeath(request.getDateOfDeath());
        reservation.setAttendees(prepared.attendees());
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setTotalAmount(prepared.amount());
        reservation.setNote(blankToNull(request.getNote()));
        reservation.getExtras().clear();
        attachExtras(reservation, prepared.extras(), prepared.attendees());
        applyPhone(actor, request);
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("venueId", saved.getVenue().getId());
        details.put("status", saved.getStatus());
        details.put("attendees", saved.getAttendees());
        details.put("package", saved.getFuneralPackage());
        details.put("totalPln", saved.getTotalAmount());
        auditLogService.record(actor, "RESERVATION_UPDATE", "RESERVATION", saved.getId(), "SUCCESS", details);
        notify(saved, NotificationType.RESERVATION_UPDATED, "Arrangement updated", "Your pending arrangement was updated.");
        return new ReservationUpdateResult(saved, previous, saved.getTotalAmount());
    }

    @Override
    @Transactional
    public void cancel(User actor, Long reservationId) {
        cancel(actor, reservationId, "CHANGE_OF_PLANS", null);
    }

    @Override
    @Transactional
    public void cancel(User actor, Long reservationId, String reason) {
        cancel(actor, reservationId, reason, null);
    }

    @Override
    @Transactional
    public void cancel(User actor, Long reservationId, String reason, String otherNote) {
        Reservation reservation = reservationRepository.lockById(reservationId)
                .orElseThrow(() -> fail(actor, "NOT_FOUND", "That arrangement could not be found"));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw fail(actor, "CONFIRMED", "Confirmed arrangements cannot be cancelled");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }
        boolean owner = reservation.getUser().getId().equals(actor.getId());
        if (!owner && !isStaff(actor)) {
            throw fail(actor, "FORBIDDEN", "You cannot cancel this arrangement");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancellationReason(safeReason(reason));
        reservationRepository.save(reservation);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("venueId", reservation.getVenue().getId());
        details.put("reasonCode", reservation.getCancellationReason());
        auditLogService.record(actor, "RESERVATION_CANCEL", "RESERVATION", reservation.getId(), "SUCCESS", details);
        notify(reservation, NotificationType.RESERVATION_CANCELLED, "Arrangement cancelled", "The pending arrangement was cancelled.");
    }

    @Override
    @Transactional
    public void confirm(User actor, Long reservationId) {
        if (!isStaff(actor)) {
            throw fail(actor, "FORBIDDEN", "A manager must confirm this arrangement");
        }
        Reservation reservation = reservationRepository.lockById(reservationId)
                .orElseThrow(() -> fail(actor, "NOT_FOUND", "That arrangement could not be found"));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw fail(actor, "STATUS", "Only pending arrangements can be confirmed");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("venueId", reservation.getVenue().getId());
        details.put("status", reservation.getStatus());
        auditLogService.record(actor, "RESERVATION_CONFIRM", "RESERVATION", reservation.getId(), "SUCCESS", details);
        notify(reservation, NotificationType.RESERVATION_CONFIRMED, "Arrangement confirmed", "Your ceremony time has been confirmed.");
    }

    @Override
    public List<Reservation> findForUser(User user) {
        if (user.getRole() == Role.ADMIN) {
            return reservationRepository.findAllWithDetails();
        }
        return reservationRepository.findByUserIdWithDetails(user.getId());
    }

    @Override
    public List<Reservation> findAll() {
        return reservationRepository.findAllWithDetails();
    }

    @Override
    public List<Reservation> findManagerQueue(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(WARSAW) : date;
        return reservationRepository.findQueueForDate(
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
        );
    }

    @Override
    public long countUpcomingActive(User user) {
        return reservationRepository.countByUserAndStatusInAndStartAtAfter(
                user,
                ReservationStatus.occupying(),
                LocalDateTime.now(WARSAW)
        );
    }

    @Override
    @Transactional
    public int deleteEndedBefore(LocalDateTime cutoff) {
        reservationExtraRepository.deleteForReservationsEndedBefore(cutoff);
        return reservationRepository.deleteEndedBefore(cutoff);
    }

    @Override
    @Transactional
    public int deleteEndedOlderThanOneMonth() {
        return deleteEndedBefore(LocalDateTime.now(WARSAW).minusMonths(1));
    }

    @Override
    public Optional<Reservation> findWithDetails(Long id) {
        return reservationRepository.findWithDetailsById(id);
    }

    @Override
    public boolean canEdit(User actor, Reservation reservation) {
        if (actor == null || reservation == null) {
            return false;
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            return false;
        }
        return isStaff(actor) || reservation.getUser().getId().equals(actor.getId());
    }

    private Prepared prepare(User user, ArrangementRequest request, boolean requirePhone) {
        ServiceVenue venue = serviceVenueRepository.findEnabledWithHome(request.getVenueId())
                .orElseThrow(() -> fail(user, "VENUE_NOT_FOUND", "venueId", "That venue could not be found"));
        return prepare(user, request, requirePhone, venue);
    }

    private Prepared prepare(User user, ArrangementRequest request, boolean requirePhone, ServiceVenue venue) {
        if (!venue.isEnabled() || venue.getFuneralHome() == null || !venue.getFuneralHome().isEnabled()) {
            throw fail(user, "VENUE_CLOSED", "venueId", "This home is not open for arrangements");
        }
        if (request.getPaymentMethod() == null) {
            throw fail(user, "PAYMENT_METHOD_REQUIRED", "paymentMethod", "Choose a payment method");
        }
        ServiceType serviceType = request.getServiceType();
        if (serviceType == null || !serviceType.allows(venue.getType())) {
            throw fail(user, "SERVICE_TYPE", "serviceType", "That ceremony does not fit this venue");
        }
        FuneralPackage funeralPackage = request.getFuneralPackage();
        if (funeralPackage == null) {
            throw fail(user, "PACKAGE", "funeralPackage", "Choose a package");
        }
        if (request.getDeceasedFullName() == null || request.getDeceasedFullName().isBlank()) {
            throw fail(user, "DECEASED_NAME", "deceasedFullName", "Enter the name to remember");
        }
        if (request.getDateOfDeath() == null) {
            throw fail(user, "DATE_OF_DEATH", "dateOfDeath", "Enter the date of death");
        }
        if (request.getDateOfBirth() != null && request.getDateOfDeath().isBefore(request.getDateOfBirth())) {
            throw fail(user, "DATES", "dateOfDeath", "Date of death cannot be earlier than date of birth");
        }
        int attendees = request.getAttendees() == null ? 1 : request.getAttendees();
        if (attendees < 1 || attendees > venue.getMaxAttendees()) {
            throw fail(user, "ATTENDEES", "attendees", "Guest count must be between 1 and " + venue.getMaxAttendees());
        }
        if (requirePhone) {
            boolean missing = user.getPhone() == null || user.getPhone().isBlank();
            if (missing && (request.getPhone() == null || request.getPhone().isBlank())) {
                throw fail(user, "PHONE_REQUIRED", "phone", "Phone is required to complete this arrangement");
            }
            if (request.getPhone() != null && !request.getPhone().isBlank() && !PHONE.matcher(request.getPhone().trim()).matches()) {
                throw fail(user, "PHONE_INVALID", "phone", "Enter a valid phone number");
            }
        }
        List<ArrangementExtra> extras = resolveExtras(request.getExtraIds(), serviceType);
        BigDecimal amount = pricingService.quote(funeralPackage, extras, attendees);
        return new Prepared(venue, serviceType, funeralPackage, attendees, extras, amount);
    }

    private List<ArrangementExtra> resolveExtras(List<Long> extraIds, ServiceType serviceType) {
        if (extraIds == null || extraIds.isEmpty()) {
            return List.of();
        }
        List<ArrangementExtra> extras = new ArrayList<>();
        for (Long extraId : extraIds) {
            if (extraId == null) {
                continue;
            }
            arrangementExtraRepository.findById(extraId).ifPresent(item -> {
                if (item.isEnabled() && item.appliesTo(serviceType)) {
                    extras.add(item);
                }
            });
        }
        return extras;
    }

    private void attachExtras(Reservation reservation, List<ArrangementExtra> extras, int attendees) {
        for (ArrangementExtra item : extras) {
            ReservationExtra line = new ReservationExtra();
            line.setReservation(reservation);
            line.setItem(item);
            int quantity = item.getPricingMode() == PricingMode.PER_ATTENDEE ? attendees : 1;
            line.setQuantity(quantity);
            line.setUnitAmount(item.getAmount());
            line.setDescription(item.getName());
            reservation.getExtras().add(line);
        }
    }

    private void applyPhone(User user, ArrangementRequest request) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return;
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            userService.updatePhone(user, request.getPhone().trim());
        }
    }

    private void auditCreated(Reservation saved, User actor, ArrangementRequest request, int candidateCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("venueId", saved.getVenue().getId());
        details.put("startAt", saved.getStartAt());
        details.put("endAt", saved.getEndAt());
        details.put("status", saved.getStatus());
        details.put("attendees", saved.getAttendees());
        details.put("serviceType", saved.getServiceType());
        details.put("package", saved.getFuneralPackage());
        details.put("paymentMethod", saved.getPaymentMethod());
        details.put("totalPln", saved.getTotalAmount());
        details.put("candidates", candidateCount);
        if (request.getBookingSource() != null && !request.getBookingSource().isBlank()) {
            details.put("source", request.getBookingSource().trim());
        } else {
            details.put("source", "FORM");
        }
        auditLogService.record(actor, "RESERVATION_CREATE", "RESERVATION", saved.getId(), "SUCCESS", details);
        if ("CHAT_ASSISTANT".equals(request.getBookingSource())) {
            Map<String, Object> assistant = new LinkedHashMap<>();
            assistant.put("source", "CHAT_ASSISTANT");
            assistant.put("venueId", saved.getVenue().getId());
            assistant.put("status", saved.getStatus());
            assistant.put("totalPln", saved.getTotalAmount());
            assistant.put("startAt", saved.getStartAt());
            auditLogService.record(actor, "ASSISTANT_RESERVATION_SUCCESS", "RESERVATION", saved.getId(), "SUCCESS", assistant);
        }
        if ("TELEGRAM".equals(request.getBookingSource())) {
            Map<String, Object> telegram = new LinkedHashMap<>();
            telegram.put("source", "TELEGRAM");
            telegram.put("venueId", saved.getVenue().getId());
            telegram.put("status", saved.getStatus());
            telegram.put("totalPln", saved.getTotalAmount());
            telegram.put("startAt", saved.getStartAt());
            telegram.put("attendees", saved.getAttendees());
            auditLogService.record(actor, "TELEGRAM_RESERVATION_SUCCESS", "RESERVATION", saved.getId(), "SUCCESS", telegram);
        }
    }

    private void notify(Reservation reservation, NotificationType type, String title, String message) {
        notificationService.create(reservation.getUser(), type, title, message, reservation.getId());
    }

    private String expectedStatus(User user) {
        return isStaff(user) ? ReservationStatus.CONFIRMED.name() : ReservationStatus.PENDING.name();
    }

    private boolean isStaff(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return CancellationReason.CHANGE_OF_PLANS.name();
        }
        try {
            return CancellationReason.valueOf(reason.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            return CancellationReason.OTHER.name();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ReservationException fail(User user, String code, String message) {
        return fail(user, code, null, message);
    }

    private ReservationException fail(User user, String code, String field, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", code);
        auditLogService.record(user, "RESERVATION_REJECTED", "RESERVATION", null, "REJECTED", details);
        return new ReservationException(code, field, message);
    }

    private Reservation replayExisting(User user, Reservation found) {
        if (found.getUser() == null || !found.getUser().getId().equals(user.getId())) {
            throw fail(user, "SUBMISSION", "submissionToken", "That confirmation could not be repeated");
        }
        return reservationRepository.findWithDetailsById(found.getId()).orElse(found);
    }

    private ArrangementCreateResponse toCreateResponse(Reservation saved) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate winner = saved.getStartAt().toLocalDate();
        dates.add(winner);
        List<LocalDate> more = dateAssignmentService.previewDates(saved.getVenue(), saved.getFuneralPackage());
        for (LocalDate date : more) {
            if (!dates.contains(date)) {
                dates.add(date);
            }
        }
        return new ArrangementCreateResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getTotalAmount(),
                saved.getFormattedTotalAmount(),
                saved.getStartAt(),
                saved.getEndAt(),
                dates
        );
    }

    private record Prepared(
            ServiceVenue venue,
            ServiceType serviceType,
            FuneralPackage funeralPackage,
            int attendees,
            List<ArrangementExtra> extras,
            BigDecimal amount
    ) {
    }
}
