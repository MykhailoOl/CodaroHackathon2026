package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.intent.derive.ArrangementFacts;
import com.example.hackathoncodaro2026.intent.derive.ArrangementFactsParser;
import com.example.hackathoncodaro2026.intent.derive.BurialWindowService;
import com.example.hackathoncodaro2026.intent.derive.ServiceWindow;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.model.enums.VenueType;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.DateAssignmentService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.voice.dto.AvailabilitySlot;
import com.example.hackathoncodaro2026.voice.dto.CheckAvailabilityRequest;
import com.example.hackathoncodaro2026.voice.dto.CheckAvailabilityResponse;
import com.example.hackathoncodaro2026.voice.dto.CreateBookingRequest;
import com.example.hackathoncodaro2026.voice.dto.CreateBookingResponse;
import com.example.hackathoncodaro2026.voice.invite.VoiceInviteService;
import com.example.hackathoncodaro2026.voice.sms.SmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoiceBookingService {

    private static final Logger log = LoggerFactory.getLogger(VoiceBookingService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final VoiceProperties properties;
    private final ArrangementFactsParser factsParser;
    private final BurialWindowService burialWindowService;
    private final CatalogService catalogService;
    private final DateAssignmentService dateAssignmentService;
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsClient smsClient;
    private final VoiceInviteService voiceInviteService;

    public VoiceBookingService(
            VoiceProperties properties,
            ArrangementFactsParser factsParser,
            BurialWindowService burialWindowService,
            CatalogService catalogService,
            DateAssignmentService dateAssignmentService,
            ReservationService reservationService,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SmsClient smsClient,
            VoiceInviteService voiceInviteService
    ) {
        this.properties = properties;
        this.factsParser = factsParser;
        this.burialWindowService = burialWindowService;
        this.catalogService = catalogService;
        this.dateAssignmentService = dateAssignmentService;
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsClient = smsClient;
        this.voiceInviteService = voiceInviteService;
    }

    public CheckAvailabilityResponse checkAvailability(CheckAvailabilityRequest request) {
        String text = request.resolvedText();
        if (isBlank(text)) {
            throw VoiceToolException.validation("Please tell me the date of death, and whether this is a burial or cremation.");
        }
        LocalDate today = LocalDate.now(WARSAW);
        ArrangementFacts facts = factsParser.parse(text, today);
        if (!facts.schedulable()) {
            throw VoiceToolException.validation("I need the date of death before I can propose a ceremony time.");
        }
        ServiceWindow window = burialWindowService.derive(facts, LocalDateTime.now(WARSAW)).orElse(null);
        LocalDate earliest = window == null ? facts.dateOfDeath().plusDays(1) : window.earliest();
        LocalDate latest = window == null ? null : window.latest();
        ServiceType serviceType = inferService(text);
        FuneralPackage funeralPackage = inferPackage(text);
        int attendees = resolveAttendees(request.getPartySize(), facts.mourners());
        ServiceVenue venue = pickVenue(serviceType);
        if (venue == null) {
            throw VoiceToolException.validation("I could not find a chapel for that service.");
        }
        LocalDateTime start = nthStart(venue, funeralPackage, earliest, latest, request.skipCount());
        if (start == null) {
            throw VoiceToolException.validation("Nothing is free inside that window. A director has to take this by hand.");
        }
        LocalDateTime end = start.plusMinutes(funeralPackage.getDurationMinutes());
        String label = SpokenLabels.slot(
                venue.getName(),
                venue.getFuneralHome() == null ? null : venue.getFuneralHome().getName(),
                start,
                end
        );
        AvailabilitySlot slot = new AvailabilitySlot(
                encodeSlot(venue.getId(), start, end, attendees, serviceType, funeralPackage, facts.dateOfDeath()),
                label,
                venue.getId(),
                start,
                end,
                funeralPackage.getBasePrice().toPlainString() + " PLN",
                attendees
        );
        return new CheckAvailabilityResponse(List.of(slot), false);
    }

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        DecodedSlot slot = decodeBooking(request);
        if (slot.dateOfDeath() == null) {
            throw VoiceToolException.validation("I need the date of death before I can arrange the ceremony.");
        }
        User caller = resolveCaller(request);
        ArrangementRequest arrangement = new ArrangementRequest();
        arrangement.setVenueId(slot.venueId());
        arrangement.setServiceType(slot.serviceType());
        arrangement.setFuneralPackage(slot.funeralPackage());
        arrangement.setDeceasedFullName(resolveDeceasedName(request));
        arrangement.setDateOfDeath(slot.dateOfDeath());
        arrangement.setAttendees(slot.attendees());
        arrangement.setPaymentMethod(PaymentMethod.CASH);
        arrangement.setPhone(caller.getPhone());
        arrangement.setBookingSource("PHONE");
        arrangement.setCeremonyStart(slot.start());
        Reservation saved;
        try {
            saved = reservationService.create(caller, arrangement);
        } catch (ReservationException ex) {
            log.info("Voice arrangement rejected field={} message={}", ex.getField(), ex.getMessage());
            throw VoiceToolException.slotGone("That time was just taken. I can propose the next one.");
        }
        saved.setInviteToken(newInviteToken());
        saved = reservationRepository.save(saved);
        String inviteUrl = voiceInviteService.inviteUrl(saved.getInviteToken());
        String confirmation = "Arranged "
                + SpokenLabels.slot(
                        saved.getVenue().getName(),
                        saved.getVenue().getFuneralHome() == null ? null : saved.getVenue().getFuneralHome().getName(),
                        saved.getStartAt(),
                        saved.getEndAt()
                )
                + ".";
        String smsStatus = sendSms(request.getPlayerPhone(), saved, inviteUrl);
        return new CreateBookingResponse(String.valueOf(saved.getId()), confirmation, smsStatus, inviteUrl);
    }

    public VoiceCatalog catalog() {
        String base = trimSlash(properties.getPublicBaseUrl());
        boolean phoneReady = notBlank(properties.getElevenlabs().getPhoneNumberId());
        return new VoiceCatalog(
                List.of(
                        new VoiceCatalog.Tool(
                                "check_availability",
                                base + "/api/voice/tools/check-availability",
                                "Propose the earliest ceremony inside the legal window. Pass the family's words as text."
                        ),
                        new VoiceCatalog.Tool(
                                "create_booking",
                                base + "/api/voice/tools/create-booking",
                                "Confirm the proposed ceremony. Pass venueId, start, and end from the last proposal."
                        )
                ),
                new VoiceCatalog.PhoneWiring(
                        phoneReady ? "ready" : "placeholder",
                        properties.getTelephony().getProvider(),
                        phoneReady
                                ? "Number id is set. Assign it to the agent."
                                : "Set SIP_FROM_NUMBER, then assign that number to the agent."
                ),
                new VoiceCatalog.Wiring(
                        properties.getElevenlabs().isConfigured() ? "ready" : "placeholder",
                        "elevenlabs",
                        "Create the agent from docs/VOICE.md or POST /api/voice/provision."
                ),
                new VoiceCatalog.Wiring(
                        properties.getTelephony().sipConfigured() ? "ready" : "placeholder",
                        properties.getTelephony().getProvider(),
                        "Import the spare Telnyx SIP trunk in ElevenLabs."
                )
        );
    }

    private LocalDateTime nthStart(
            ServiceVenue venue,
            FuneralPackage funeralPackage,
            LocalDate earliest,
            LocalDate latest,
            int skip
    ) {
        int remaining = Math.max(0, skip);
        for (LocalDateTime start : dateAssignmentService.availableStarts(venue, funeralPackage)) {
            LocalDate day = start.toLocalDate();
            if (earliest != null && day.isBefore(earliest)) {
                continue;
            }
            if (latest != null && day.isAfter(latest)) {
                continue;
            }
            if (remaining > 0) {
                remaining--;
                continue;
            }
            return start;
        }
        return null;
    }

    private ServiceVenue pickVenue(ServiceType serviceType) {
        List<ServiceVenue> matches = new ArrayList<>();
        for (FuneralHome home : catalogService.homes()) {
            for (ServiceVenue venue : catalogService.venues(home.getId())) {
                if (venue.isEnabled() && serviceType.allows(venue.getType())) {
                    matches.add(venue);
                }
            }
        }
        if (serviceType == ServiceType.CREMATION_CEREMONY) {
            for (ServiceVenue venue : matches) {
                if (venue.getType() == VenueType.CREMATORIUM) {
                    return venue;
                }
            }
        }
        for (ServiceVenue venue : matches) {
            if ("Willow Chapel".equals(venue.getName())) {
                return venue;
            }
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private ServiceType inferService(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("cremat")) {
            return ServiceType.CREMATION_CEREMONY;
        }
        if (lower.contains("memorial")) {
            return ServiceType.MEMORIAL_SERVICE;
        }
        if (lower.contains("farewell")) {
            return ServiceType.FAREWELL_CEREMONY;
        }
        return ServiceType.BURIAL_CEREMONY;
    }

    private FuneralPackage inferPackage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("essential") || lower.contains("simple") || lower.contains("quiet")) {
            return FuneralPackage.ESSENTIAL;
        }
        if (lower.contains("tribute") || lower.contains("extended")) {
            return FuneralPackage.TRIBUTE;
        }
        return FuneralPackage.CLASSIC;
    }

    private int resolveAttendees(Integer requested, Integer parsed) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (parsed != null && parsed > 0) {
            return parsed;
        }
        return 20;
    }

    private DecodedSlot decodeBooking(CreateBookingRequest request) {
        if (request.getResourceId() != null && request.getStart() != null && request.getEnd() != null) {
            return new DecodedSlot(
                    request.getResourceId(),
                    request.getStart(),
                    request.getEnd(),
                    resolveAttendees(request.getPartySize(), null),
                    inferService(blankToEmpty(request.getNotes())),
                    inferPackage(blankToEmpty(request.getNotes())),
                    request.getDateOfDeath()
            );
        }
        if (isBlank(request.getSlotId())) {
            throw VoiceToolException.validation("I need the ceremony I just proposed.");
        }
        String[] parts = request.getSlotId().split("\\|", 7);
        if (parts.length < 7) {
            throw VoiceToolException.validation("That proposal is not valid. I can propose a time again.");
        }
        try {
            return new DecodedSlot(
                    Long.parseLong(parts[0]),
                    LocalDateTime.parse(parts[1], ISO),
                    LocalDateTime.parse(parts[2], ISO),
                    Integer.parseInt(parts[3]),
                    ServiceType.valueOf(parts[4]),
                    FuneralPackage.valueOf(parts[5]),
                    LocalDate.parse(parts[6])
            );
        } catch (RuntimeException ex) {
            throw VoiceToolException.validation("That proposal is not valid. I can propose a time again.");
        }
    }

    private String encodeSlot(
            long venueId,
            LocalDateTime start,
            LocalDateTime end,
            int attendees,
            ServiceType serviceType,
            FuneralPackage funeralPackage,
            LocalDate dateOfDeath
    ) {
        return venueId + "|" + ISO.format(start) + "|" + ISO.format(end) + "|" + attendees + "|"
                + serviceType.name() + "|" + funeralPackage.name() + "|" + dateOfDeath;
    }

    private String resolveDeceasedName(CreateBookingRequest request) {
        if (!isBlank(request.getDeceasedFullName())) {
            return request.getDeceasedFullName().trim();
        }
        if (!isBlank(request.getPlayerName()) && !request.getPlayerName().toLowerCase(Locale.ROOT).contains("caller")) {
            return request.getPlayerName().trim();
        }
        return "Family arrangement";
    }

    private User resolveCaller(CreateBookingRequest request) {
        String phone = trimToNull(request.getPlayerPhone());
        if (phone != null) {
            Optional<User> byPhone = userRepository.findFirstByPhone(phone);
            if (byPhone.isPresent()) {
                return byPhone.get();
            }
        }
        String username = "v" + (phone == null ? UUID.randomUUID().toString().replace("-", "").substring(0, 12) : phone.replaceAll("\\D", ""));
        Optional<User> existing = userRepository.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(username.toLowerCase(Locale.ROOT) + "@everrest.local");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setFullName(isBlank(request.getPlayerName()) ? "Phone caller" : request.getPlayerName().trim());
        user.setPhone(phone);
        user.setRole(Role.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private String sendSms(String phone, Reservation reservation, String inviteUrl) {
        if (isBlank(phone) || !properties.getSms().isEnabled()) {
            log.info("SMS skipped phonePresent={} enabled={}", notBlank(phone), properties.getSms().isEnabled());
            return "skipped";
        }
        String body = "EverRest pending arrangement for "
                + reservation.getDeceasedFullName()
                + "\n"
                + SpokenLabels.slot(
                        reservation.getVenue().getName(),
                        reservation.getVenue().getFuneralHome() == null ? null : reservation.getVenue().getFuneralHome().getName(),
                        reservation.getStartAt(),
                        reservation.getEndAt()
                )
                + "\n"
                + reservation.getServiceType().getLabel()
                + " · "
                + reservation.getFuneralPackage().getLabel()
                + " · "
                + reservation.getAttendees()
                + " guests"
                + "\nA director will confirm."
                + "\nCalendar: "
                + inviteUrl;
        return smsClient.send(phone.trim(), body);
    }

    private String newInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private record DecodedSlot(
            Long venueId,
            LocalDateTime start,
            LocalDateTime end,
            int attendees,
            ServiceType serviceType,
            FuneralPackage funeralPackage,
            LocalDate dateOfDeath
    ) {
    }

    public record VoiceCatalog(
            List<Tool> tools,
            PhoneWiring phoneNumberWiring,
            Wiring agentProvisioning,
            Wiring sipWiring
    ) {
        public record Tool(String name, String url, String description) {
        }

        public record PhoneWiring(String status, String provider, String detail) {
        }

        public record Wiring(String status, String provider, String detail) {
        }
    }
}
