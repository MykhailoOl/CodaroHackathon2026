package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.intent.parse.IntentParseException;
import com.example.hackathoncodaro2026.intent.service.IntentBookingService;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService.PricedSuggestion;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService.SuggestOutcome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoiceBookingService {

    private static final Logger log = LoggerFactory.getLogger(VoiceBookingService.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final VoiceProperties properties;
    private final IntentSchedulingService intentSchedulingService;
    private final IntentBookingService intentBookingService;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsClient smsClient;
    private final VoiceInviteService voiceInviteService;

    public VoiceBookingService(
            VoiceProperties properties,
            IntentSchedulingService intentSchedulingService,
            IntentBookingService intentBookingService,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SmsClient smsClient,
            VoiceInviteService voiceInviteService
    ) {
        this.properties = properties;
        this.intentSchedulingService = intentSchedulingService;
        this.intentBookingService = intentBookingService;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsClient = smsClient;
        this.voiceInviteService = voiceInviteService;
    }

    public CheckAvailabilityResponse checkAvailability(CheckAvailabilityRequest request) {
        String text = request.resolvedText();
        if (isBlank(text)) {
            throw VoiceToolException.validation("Tell me the sport and when you want to play.");
        }
        int partySize = resolvePartySize(request.getPartySize());
        SuggestOutcome outcome;
        try {
            outcome = intentSchedulingService.suggest(text, partySize, 0L);
        } catch (IntentParseException ex) {
            throw VoiceToolException.validation(ex.getMessage());
        }
        List<AvailabilitySlot> slots = new ArrayList<>();
        int limit = Math.min(properties.getMaxSlots(), outcome.suggestions().size());
        for (int i = 0; i < limit; i++) {
            PricedSuggestion priced = outcome.suggestions().get(i);
            var suggestion = priced.suggestion();
            String label = suggestion.resourceName()
                    + (suggestion.facilityName() == null ? "" : " at " + suggestion.facilityName())
                    + ", "
                    + CLOCK.format(suggestion.start().toLocalTime())
                    + "–"
                    + CLOCK.format(suggestion.end().toLocalTime())
                    + " "
                    + suggestion.start().getDayOfWeek().getDisplayName(TextStyle.FULL, locale(language(request.getLanguage())));
            if (priced.price() != null) {
                label = label + ", " + priced.price();
            }
            slots.add(new AvailabilitySlot(
                    encodeSlot(suggestion.resourceId(), suggestion.start(), suggestion.end(), partySize),
                    label,
                    suggestion.resourceId(),
                    suggestion.start(),
                    suggestion.end(),
                    priced.price(),
                    partySize
            ));
        }
        if (slots.isEmpty()) {
            throw VoiceToolException.validation("I could not find an open slot for that. Try another day or sport.");
        }
        return new CheckAvailabilityResponse(slots, !outcome.relaxationTrail().isEmpty());
    }

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        DecodedSlot slot = decodeBooking(request);
        User player = resolvePlayer(request);
        Reservation saved;
        try {
            saved = intentBookingService.book(
                    player,
                    slot.resourceId(),
                    slot.start(),
                    slot.end(),
                    slot.partySize(),
                    PaymentMethod.CASH
            );
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
        boolean phoneReady = notBlank(properties.getElevenlabs().getPhoneNumberId());
        return new VoiceCatalog(
                List.of(
                        new VoiceCatalog.Tool(
                                "check_availability",
                                base + "/api/voice/tools/check-availability",
                                "Same intent suggest as the chatbot. Pass the caller's request as text."
                        ),
                        new VoiceCatalog.Tool(
                                "create_booking",
                                base + "/api/voice/tools/create-booking",
                                "Same intent book as the chatbot. Pass resourceId, start, and end from the last suggestion."
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

    private DecodedSlot decodeBooking(CreateBookingRequest request) {
        Integer requestedPartySize = request.getPartySize();
        if (request.getResourceId() != null && request.getStart() != null && request.getEnd() != null) {
            return new DecodedSlot(
                    request.getResourceId(),
                    request.getStart(),
                    request.getEnd(),
                    resolvePartySize(requestedPartySize)
            );
        }
        if (isBlank(request.getSlotId())) {
            throw VoiceToolException.validation("I need a slot from the last availability check.");
        }
        String[] parts = request.getSlotId().split("\\|", 4);
        if (parts.length < 3) {
            throw VoiceToolException.validation("That slot is not valid. Check availability again.");
        }
        try {
            Integer encodedPartySize = parts.length >= 4 ? Integer.parseInt(parts[3]) : null;
            return new DecodedSlot(
                    Long.parseLong(parts[0]),
                    LocalDateTime.parse(parts[1], ISO),
                    LocalDateTime.parse(parts[2], ISO),
                    resolvePartySize(requestedPartySize != null ? requestedPartySize : encodedPartySize)
            );
        } catch (RuntimeException ex) {
            throw VoiceToolException.validation("That slot is not valid. Check availability again.");
        }
    }

    private String encodeSlot(long resourceId, LocalDateTime start, LocalDateTime end, int partySize) {
        return resourceId + "|" + ISO.format(start) + "|" + ISO.format(end) + "|" + partySize;
    }

    private int resolvePartySize(Integer partySize) {
        if (partySize == null || partySize < 1) {
            return 2;
        }
        return partySize;
    }

    private String newInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
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
        return username.length() > 32 ? username.substring(0, 32) : username;
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
        return "Booked "
                + reservation.getResource().getName()
                + " on "
                + reservation.getStartAt().toLocalDate().getDayOfWeek().getDisplayName(TextStyle.FULL, locale(language))
                + " at "
                + CLOCK.format(reservation.getStartAt().toLocalTime())
                + ".";
    }

    private String callerSafeConflict(String message) {
        if (message == null || message.isBlank()) {
            return "That time was just taken. I can offer another slot.";
        }
        return message;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record DecodedSlot(long resourceId, LocalDateTime start, LocalDateTime end, int partySize) {
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
