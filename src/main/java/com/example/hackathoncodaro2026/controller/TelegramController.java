package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.telegram.TelegramHistoryItem;
import com.example.hackathoncodaro2026.dto.telegram.TelegramTokenRequest;
import com.example.hackathoncodaro2026.dto.telegram.TelegramTokenResponse;
import com.example.hackathoncodaro2026.dto.telegram.TelegramVenueCard;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.TelegramTokenService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(path = "/api/telegram", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelegramController {

    private final AuthenticationManager authenticationManager;
    private final TelegramTokenService telegramTokenService;
    private final UserService userService;
    private final CatalogService catalogService;
    private final ReservationService reservationService;
    private final Validator validator;

    public TelegramController(
            AuthenticationManager authenticationManager,
            TelegramTokenService telegramTokenService,
            UserService userService,
            CatalogService catalogService,
            ReservationService reservationService,
            Validator validator
    ) {
        this.authenticationManager = authenticationManager;
        this.telegramTokenService = telegramTokenService;
        this.userService = userService;
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        this.validator = validator;
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TelegramTokenResponse token(@Valid @RequestBody TelegramTokenRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
        TelegramTokenService.Issued issued = telegramTokenService.issue(user.getUsername());
        boolean phoneRequired = user.getPhone() == null || user.getPhone().isBlank();
        String display = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
        return new TelegramTokenResponse(issued.token(), display, user.getUsername(), issued.expiresAt(), phoneRequired);
    }

    @GetMapping("/venues")
    public List<TelegramVenueCard> venues(
            @RequestParam ServiceType serviceType,
            @RequestParam(required = false) Integer attendees,
            Authentication authentication
    ) {
        requireUser(authentication);
        int guests = attendees == null ? 1 : attendees;
        List<TelegramVenueCard> cards = new ArrayList<>();
        for (ServiceVenue venue : catalogService.venues()) {
            if (!serviceType.allows(venue.getType())) {
                continue;
            }
            if (venue.getMaxAttendees() < guests) {
                continue;
            }
            String homeName = venue.getFuneralHome() == null ? "" : venue.getFuneralHome().getName();
            String address = venue.getAddress() == null ? "" : venue.getAddress().toDisplayString();
            cards.add(new TelegramVenueCard(
                    venue.getId(),
                    venue.getName(),
                    homeName,
                    venue.getType().getLabel(),
                    venue.getMaxAttendees(),
                    address
            ));
        }
        return cards;
    }

    @GetMapping("/history")
    public List<TelegramHistoryItem> history(Authentication authentication) {
        User user = requireUser(authentication);
        List<TelegramHistoryItem> items = new ArrayList<>();
        for (Reservation reservation : reservationService.findForUser(user)) {
            ServiceVenue venue = reservation.getVenue();
            String homeName = venue == null || venue.getFuneralHome() == null ? "" : venue.getFuneralHome().getName();
            items.add(new TelegramHistoryItem(
                    reservation.getId(),
                    venue == null ? "" : venue.getName(),
                    homeName,
                    reservation.getStartAt(),
                    reservation.getEndAt(),
                    reservation.getStatus() == null ? "" : reservation.getStatus().name(),
                    reservation.getFormattedTotalAmount(),
                    reservation.getAttendees()
            ));
        }
        return items;
    }

    @PostMapping(path = "/arrangements", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ArrangementCreateResponse arrange(
            @RequestBody ArrangementRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        if (request.getFuneralPackage() == null) {
            request.setFuneralPackage(FuneralPackage.ESSENTIAL);
        }
        if (request.getPaymentMethod() == null) {
            request.setPaymentMethod(PaymentMethod.CASH);
        }
        request.setBookingSource("TELEGRAM");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return reservationService.spin(user, request);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ReservationException("UNAUTHENTICATED", null, "Sign in to arrange a ceremony.");
        }
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
    }
}
