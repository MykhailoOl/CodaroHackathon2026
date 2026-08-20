package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.dto.TimeSlotView;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.ResourceService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoiceBookingService {

    private static final Logger log = LoggerFactory.getLogger(VoiceBookingService.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final VoiceProperties properties;
    private final ResourceService resourceService;
    private final ReservationService reservationService;
    private final SportResourceRepository sportResourceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsClient smsClient;
    private final VoiceInviteService voiceInviteService;
    private final SlotCodec slotCodec;

    public VoiceBookingService(
            VoiceProperties properties,
            ResourceService resourceService,
            ReservationService reservationService,
            SportResourceRepository sportResourceRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SmsClient smsClient,
            VoiceInviteService voiceInviteService
    ) {
        this.properties = properties;
        this.resourceService = resourceService;
        this.reservationService = reservationService;
        this.sportResourceRepository = sportResourceRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsClient = smsClient;
        this.voiceInviteService = voiceInviteService;
        this.slotCodec = new SlotCodec(properties.getToolWebhookSecret());
    }

    public CheckAvailabilityResponse checkAvailability(CheckAvailabilityRequest request) {
        ResourceType sport = parseSport(request.getSport());
        SportResource resource = pickResource(sport);
        ReservationKind kind = bookingKind(resource);
        int hours = resolveHours(request.getDurationHours());
        ZoneId zone = zone();
        LocalDate date = resolveDay(request.getPreferredDay(), zone);
        LocalTime preferredTime = parseClock(request.getPreferredTime());
        String partOfDay = request.resolvedPartOfDay();

        List<TimeSlotView> available = openSlots(resource, date, kind, hours);
        boolean widened = false;
        List<TimeSlotView> filtered = filterWindow(available, partOfDay, preferredTime);
        if (filtered.isEmpty() && (!isBlank(partOfDay) || preferredTime != null)) {
            filtered = available;
            widened = !available.isEmpty();
        }
        List<TimeSlotView> chosen = spread(sortForPreference(filtered, preferredTime), properties.getMaxSlots());
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (TimeSlotView slot : chosen) {
            slots.add(new AvailabilitySlot(
                    slotCodec.encode(resource.getId(), date, slot.getStart(), hours, kind),
                    displayLabel(resource, date, slot, hours, language(request.getLanguage()))
            ));
        }
        return new CheckAvailabilityResponse(slots, widened);
    }

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        if (isBlank(request.getSlotId())) {
            throw VoiceToolException.validation("I need a slot from the last availability check.");
        }
        SlotCodec.DecodedSlot decoded = slotCodec.decode(request.getSlotId());
        SportResource resource = sportResourceRepository.findWithFacilityById(decoded.resourceId())
                .filter(item -> item.isEnabled() && item.getFacility() != null && item.getFacility().isEnabled())
                .orElseThrow(() -> VoiceToolException.validation("That court is not available right now."));
        User player = resolvePlayer(request);
        ReservationRequest reservationRequest = new ReservationRequest();
        reservationRequest.setResourceId(resource.getId());
        reservationRequest.setDate(decoded.date());
        reservationRequest.setStartTime(decoded.start());
        reservationRequest.setDurationHours(decoded.durationHours());
        reservationRequest.setKind(decoded.kind());
        reservationRequest.setPaymentMethod(PaymentMethod.CASH);
        reservationRequest.setPhone(request.getPlayerPhone());
        if (resource.requiresAttendeeCount(decoded.kind())) {
            reservationRequest.setPartySize(resource.attendeeMin(decoded.kind()));
        }
        reservationRequest.setNote(bookingNote(request));
        Reservation saved;
        try {
            saved = reservationService.create(player, reservationRequest);
        } catch (ReservationException ex) {
            log.info("Voice booking rejected field={} message={}", ex.getField(), ex.getMessage());
            throw VoiceToolException.slotGone(callerSafeConflict(ex.getMessage()));
        }
        saved.setInviteToken(newInviteToken());
        saved = reservationRepository.save(saved);
        String inviteUrl = voiceInviteService.inviteUrl(saved.getInviteToken());
        String confirmation = confirmationLine(saved, language(request.getLanguage()));
        String smsStatus = sendSms(request.getPlayerPhone(), saved, inviteUrl, language(request.getLanguage()));
        return new CreateBookingResponse(String.valueOf(saved.getId()), confirmation, smsStatus, inviteUrl);
    }

    public VoiceCatalog catalog() {
        String base = trimSlash(properties.getPublicBaseUrl());
        boolean phoneReady = properties.getElevenlabs().getPhoneNumberId() != null
                && !properties.getElevenlabs().getPhoneNumberId().isBlank();
        return new VoiceCatalog(
                List.of(
                        new VoiceCatalog.Tool(
                                "check_availability",
                                base + "/api/voice/tools/check-availability",
                                "Look up open court or gym slots in the local database."
                        ),
                        new VoiceCatalog.Tool(
                                "create_booking",
                                base + "/api/voice/tools/create-booking",
                                "Book a previously offered slot into the Courtly database."
                        )
                ),
                new VoiceCatalog.PhoneWiring(
                        phoneReady ? "ready" : "placeholder",
                        properties.getTelephony().getProvider(),
                        phoneReady
                                ? "Attach ELEVENLABS_PHONE_NUMBER_ID to the agent and inbound calls will hit these tools."
                                : "Set SIP_FROM_NUMBER / ELEVENLABS_PHONE_NUMBER_ID, then assign the number to the provisioned agent."
                ),
                new VoiceCatalog.Wiring(
                        properties.getElevenlabs().isConfigured() ? "ready" : "placeholder",
                        "elevenlabs",
                        "POST /api/voice/provision builds the agent spec from this app. Keys stay in env and can be rotated."
                ),
                new VoiceCatalog.Wiring(
                        properties.getTelephony().sipConfigured() ? "ready" : "placeholder",
                        properties.getTelephony().getProvider(),
                        "Paste Telnyx SIP username/password/from number. Import that trunk in ElevenLabs; rotate later."
                )
        );
    }

    private String newInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private List<TimeSlotView> openSlots(SportResource resource, LocalDate date, ReservationKind kind, int hours) {
        List<TimeSlotView> grid = resourceService.slotsFor(resource, date, kind);
        List<TimeSlotView> open = new ArrayList<>();
        for (int i = 0; i < grid.size(); i++) {
            TimeSlotView start = grid.get(i);
            if (!start.isAvailable()) {
                continue;
            }
            boolean consecutive = true;
            for (int step = 1; step < hours; step++) {
                int next = i + step;
                if (next >= grid.size() || !grid.get(next).isAvailable()) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) {
                open.add(start);
            }
        }
        return open;
    }

    private List<TimeSlotView> filterWindow(List<TimeSlotView> slots, String partOfDay, LocalTime preferredTime) {
        LocalTime from = LocalTime.MIN;
        LocalTime to = LocalTime.MAX;
        String window = partOfDay == null ? "" : partOfDay.trim().toLowerCase(Locale.ROOT);
        switch (window) {
            case "morning" -> {
                from = LocalTime.of(7, 0);
                to = LocalTime.of(12, 0);
            }
            case "afternoon" -> {
                from = LocalTime.of(12, 0);
                to = LocalTime.of(17, 0);
            }
            case "evening" -> {
                from = LocalTime.of(17, 0);
                to = LocalTime.of(22, 0);
            }
            default -> {
            }
        }
        if (preferredTime != null) {
            LocalTime bandFrom = preferredTime.minusMinutes(60);
            LocalTime bandTo = preferredTime.plusMinutes(60);
            from = preferredTime;
            to = preferredTime.plusMinutes(1);
            final LocalTime startBand = bandFrom.isBefore(LocalTime.MIN) ? LocalTime.MIN : bandFrom;
            final LocalTime endBand = bandTo;
            List<TimeSlotView> exactOrNear = new ArrayList<>();
            for (TimeSlotView slot : slots) {
                if (!slot.getStart().isBefore(startBand) && !slot.getStart().isAfter(endBand)) {
                    exactOrNear.add(slot);
                }
            }
            if (!exactOrNear.isEmpty()) {
                return exactOrNear;
            }
        }
        LocalTime windowFrom = from;
        LocalTime windowTo = to;
        List<TimeSlotView> matched = new ArrayList<>();
        for (TimeSlotView slot : slots) {
            if (!slot.getStart().isBefore(windowFrom) && slot.getStart().isBefore(windowTo)) {
                matched.add(slot);
            }
        }
        return matched;
    }

    private List<TimeSlotView> sortForPreference(List<TimeSlotView> slots, LocalTime preferredTime) {
        if (preferredTime == null) {
            return slots;
        }
        return slots.stream()
                .sorted(Comparator.comparingLong(slot -> Math.abs(minutes(slot.getStart()) - minutes(preferredTime))))
                .toList();
    }

    private List<TimeSlotView> spread(List<TimeSlotView> slots, int limit) {
        if (slots.size() <= limit) {
            return slots;
        }
        List<TimeSlotView> picked = new ArrayList<>();
        int lastIndex = slots.size() - 1;
        for (int i = 0; i < limit; i++) {
            int index = (int) Math.round(i * (lastIndex / (double) (limit - 1)));
            TimeSlotView slot = slots.get(index);
            if (!picked.contains(slot)) {
                picked.add(slot);
            }
        }
        return picked;
    }

    private User resolvePlayer(CreateBookingRequest request) {
        String phone = trimToNull(request.getPlayerPhone());
        if (phone != null) {
            Optional<User> byPhone = userRepository.findFirstByPhone(phone);
            if (byPhone.isPresent()) {
                User existing = byPhone.get();
                if (!isBlank(request.getPlayerName()) && isBlank(existing.getFullName())) {
                    existing.setFullName(request.getPlayerName().trim());
                    return userRepository.save(existing);
                }
                return existing;
            }
        }
        String name = isBlank(request.getPlayerName()) ? "Phone guest" : request.getPlayerName().trim();
        String username = voiceUsername(phone);
        Optional<User> byUsername = userRepository.findByUsernameIgnoreCase(username);
        if (byUsername.isPresent()) {
            return byUsername.get();
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(username.toLowerCase(Locale.ROOT) + "@courtly.local");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setFullName(name);
        user.setPhone(phone);
        user.setRole(Role.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private String voiceUsername(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() > 15) {
            digits = digits.substring(digits.length() - 15);
        }
        if (digits.isBlank()) {
            digits = Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
        }
        String username = "v" + digits;
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }
        return username;
    }

    private String bookingNote(CreateBookingRequest request) {
        List<String> bits = new ArrayList<>();
        bits.add("Booked by voice receptionist");
        if (!isBlank(request.getConversationId())) {
            bits.add("conversation=" + request.getConversationId().trim());
        }
        if (!isBlank(request.getNotes())) {
            bits.add(request.getNotes().trim());
        }
        String note = String.join(". ", bits);
        return note.length() > 300 ? note.substring(0, 300) : note;
    }

    private String sendSms(String phone, Reservation reservation, String inviteUrl, String language) {
        if (isBlank(phone) || !properties.getSms().isEnabled()) {
            return "skipped";
        }
        smsClient.send(phone.trim(), formatSms(reservation, inviteUrl, language));
        return "logged";
    }

    private String formatSms(Reservation reservation, String inviteUrl, String language) {
        String when = reservation.getStartAt().toLocalDate().getDayOfWeek().getDisplayName(TextStyle.FULL, locale(language))
                + " "
                + reservation.getStartAt().toLocalDate()
                + " "
                + CLOCK.format(reservation.getStartAt().toLocalTime());
        String venue = reservation.getResource().getName();
        if (reservation.getResource().getFacility() != null) {
            venue = venue + ", " + reservation.getResource().getFacility().getName();
        }
        return "Courtly booking confirmed:\n"
                + when + "\n"
                + venue + "\n"
                + "Enter your email for a calendar invitation: " + inviteUrl;
    }

    private String confirmationLine(Reservation reservation, String language) {
        String court = reservation.getResource().getName();
        String time = CLOCK.format(reservation.getStartAt().toLocalTime());
        String day = reservation.getStartAt().toLocalDate().getDayOfWeek().getDisplayName(TextStyle.FULL, locale(language));
        return "Booked " + court + " on " + day + " at " + time + ".";
    }

    private String displayLabel(SportResource resource, LocalDate date, TimeSlotView slot, int hours, String language) {
        LocalTime end = slot.getStart().plusHours(hours);
        String facility = resource.getFacility() == null ? "" : " at " + resource.getFacility().getName();
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale(language))
                + " "
                + CLOCK.format(slot.getStart())
                + "–"
                + CLOCK.format(end)
                + ", "
                + resource.getName()
                + facility;
    }

    private ResourceType parseSport(String sport) {
        if (isBlank(sport)) {
            return ResourceType.TENNIS;
        }
        String normalized = sport.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        try {
            return ResourceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw VoiceToolException.validation("I can book tennis, squash, football, basketball, volleyball, gym, or swimming.");
        }
    }

    private SportResource pickResource(ResourceType type) {
        return sportResourceRepository.findAllEnabledWithFacility().stream()
                .filter(resource -> resource.getType() == type)
                .findFirst()
                .orElseThrow(() -> VoiceToolException.validation("No open " + type.getDisplayName().toLowerCase(Locale.ROOT) + " court right now."));
    }

    private ReservationKind bookingKind(SportResource resource) {
        return resource.requiresBookingMode() ? ReservationKind.INDIVIDUAL : ReservationKind.STANDARD;
    }

    private int resolveHours(Integer durationHours) {
        int hours = durationHours == null ? properties.getDefaultDurationHours() : durationHours;
        if (hours < 1 || hours > 4) {
            throw VoiceToolException.validation("Duration must be between 1 and 4 hours.");
        }
        return hours;
    }

    private LocalDate resolveDay(String preferredDay, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (isBlank(preferredDay)) {
            return today.plusDays(1);
        }
        return switch (preferredDay.trim().toLowerCase(Locale.ROOT)) {
            case "today" -> today;
            case "tomorrow" -> today.plusDays(1);
            case "day_after_tomorrow", "day-after-tomorrow" -> today.plusDays(2);
            case "monday" -> nextOrToday(today, DayOfWeek.MONDAY);
            case "tuesday" -> nextOrToday(today, DayOfWeek.TUESDAY);
            case "wednesday" -> nextOrToday(today, DayOfWeek.WEDNESDAY);
            case "thursday" -> nextOrToday(today, DayOfWeek.THURSDAY);
            case "friday" -> nextOrToday(today, DayOfWeek.FRIDAY);
            case "saturday" -> nextOrToday(today, DayOfWeek.SATURDAY);
            case "sunday" -> nextOrToday(today, DayOfWeek.SUNDAY);
            default -> throw VoiceToolException.validation("Please say today, tomorrow, or a weekday.");
        };
    }

    private LocalDate nextOrToday(LocalDate today, DayOfWeek day) {
        int delta = day.getValue() - today.getDayOfWeek().getValue();
        if (delta < 0) {
            delta += 7;
        }
        return today.plusDays(delta);
    }

    private LocalTime parseClock(String preferredTime) {
        if (isBlank(preferredTime)) {
            return null;
        }
        String value = preferredTime.trim();
        if (!value.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
            throw VoiceToolException.validation("Preferred time must be HH:MM, for example 18:00.");
        }
        return LocalTime.parse(value.length() == 4 ? "0" + value : value);
    }

    private String callerSafeConflict(String message) {
        if (message == null || message.isBlank()) {
            return "That time was just taken. I can offer another slot.";
        }
        return message;
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimezone());
    }

    private String language(String language) {
        return isBlank(language) ? "en" : language.trim().toLowerCase(Locale.ROOT);
    }

    private Locale locale(String language) {
        return switch (language) {
            case "pl" -> Locale.forLanguageTag("pl-PL");
            case "ru" -> Locale.forLanguageTag("ru-RU");
            default -> Locale.UK;
        };
    }

    private long minutes(LocalTime time) {
        return time.getHour() * 60L + time.getMinute();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record VoiceCatalog(
            List<Tool> tools,
            PhoneWiring phoneNumberWiring,
            Wiring agentProvisioning,
            Wiring sipWiring
    ) {
        public record Tool(String name, String url, String description) {
        }

        public record PhoneWiring(String status, String provider, String nextStep) {
        }

        public record Wiring(String status, String provider, String nextStep) {
        }
    }
}
