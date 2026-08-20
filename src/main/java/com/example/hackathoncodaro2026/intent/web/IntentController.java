package com.example.hackathoncodaro2026.intent.web;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.intent.derive.ArrangementFacts;
import com.example.hackathoncodaro2026.intent.derive.ServiceWindow;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.Suggestion;
import com.example.hackathoncodaro2026.intent.service.IntentBookingService;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService.PricedSuggestion;
import com.example.hackathoncodaro2026.intent.service.IntentSchedulingService.SuggestOutcome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/intent")
public class IntentController {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final IntentSchedulingService schedulingService;
    private final IntentBookingService bookingService;
    private final ReservationService reservationService;
    private final UserService userService;

    public IntentController(
            IntentSchedulingService schedulingService,
            IntentBookingService bookingService,
            ReservationService reservationService,
            UserService userService
    ) {
        this.schedulingService = schedulingService;
        this.bookingService = bookingService;
        this.reservationService = reservationService;
        this.userService = userService;
    }

    @PostMapping("/suggest")
    public SuggestResponse suggest(@Valid @RequestBody SuggestRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        SuggestOutcome outcome = schedulingService.suggest(request.text(), request.partySize(), user.getId());
        return toResponse(outcome);
    }

    @PostMapping("/book")
    public BookResponse book(@Valid @RequestBody BookRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Reservation reservation = bookingService.book(
                user,
                request.resourceId(),
                request.start(),
                request.end(),
                request.partySize(),
                request.paymentMethod()
        );
        return new BookResponse(
                reservation.getId(),
                reservation.getStatus().name(),
                reservation.getFormattedTotalAmount(),
                "Reservation " + reservation.getStatus().name().toLowerCase() + "."
        );
    }

    /**
     * What this family has standing. Cancelled and finished arrangements are left out:
     * the caller is a chat, and what it can act on is what is still ahead.
     */
    @GetMapping("/arrangements")
    public List<ArrangementDto> arrangements(Authentication authentication) {
        User user = currentUser(authentication);
        LocalDateTime now = LocalDateTime.now(WARSAW);
        return reservationService.findForUser(user).stream()
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED)
                .filter(reservation -> reservation.getEndAt().isAfter(now))
                .sorted(Comparator.comparing(Reservation::getStartAt))
                .map(reservation -> new ArrangementDto(
                        reservation.getId(),
                        reservation.getResource().getName(),
                        reservation.getResource().getFacility().getName(),
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        reservation.getStatus().name(),
                        reservation.getFormattedTotalAmount()
                ))
                .toList();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Authenticated user could not be resolved"));
    }

    private SuggestResponse toResponse(SuggestOutcome outcome) {
        IntentSpec spec = outcome.spec();
        IntentSpecDto specDto = new IntentSpecDto(
                spec.durationMin(),
                spec.dayFrom(),
                spec.dayTo(),
                spec.timeOfDay(),
                spec.hardConstraints(),
                spec.softConstraints(),
                spec.resourceType(),
                spec.partySize()
        );
        List<SuggestionDto> suggestions = outcome.suggestions().stream().map(this::toDto).toList();
        List<RelaxStepDto> relaxationTrail = outcome.relaxationTrail().stream()
                .map(step -> new RelaxStepDto(step.action().name(), step.detail(), step.droppedKeys()))
                .toList();
        return new SuggestResponse(
                specDto,
                outcome.parserUsed(),
                suggestions,
                relaxationTrail,
                toDto(outcome.window(), outcome.rangeNote()),
                toDto(outcome.facts())
        );
    }

    private ServiceWindowDto toDto(ServiceWindow window, String note) {
        if (window == null) {
            return null;
        }
        return new ServiceWindowDto(
                window.earliest(),
                window.latest(),
                window.rite(),
                window.derivation(),
                window.decisionBy(),
                window.feasible(),
                note
        );
    }

    private ArrangementFactsDto toDto(ArrangementFacts facts) {
        if (facts == null || !facts.schedulable()) {
            return null;
        }
        return new ArrangementFactsDto(
                facts.dateOfDeath(),
                facts.rite(),
                facts.certificateReadyOn(),
                facts.mourners()
        );
    }

    private SuggestionDto toDto(PricedSuggestion priced) {
        Suggestion suggestion = priced.suggestion();
        List<ScoreTermDto> terms = suggestion.terms().stream()
                .map(term -> new ScoreTermDto(term.key(), term.label(), term.delta(), term.satisfied()))
                .toList();
        return new SuggestionDto(
                suggestion.resourceId(),
                suggestion.resourceName(),
                suggestion.facilityName(),
                suggestion.start(),
                suggestion.end(),
                suggestion.score(),
                suggestion.reason(),
                priced.price(),
                terms,
                suggestion.relaxed()
        );
    }
}
