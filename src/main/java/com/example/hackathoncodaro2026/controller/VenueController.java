package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementPreview;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller
public class VenueController {

    private final CatalogService catalogService;
    private final ReservationService reservationService;
    private final UserService userService;

    public VenueController(
            CatalogService catalogService,
            ReservationService reservationService,
            UserService userService
    ) {
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        this.userService = userService;
    }

    @GetMapping("/venues/{id}")
    public String form(@PathVariable Long id, Authentication authentication, Model model) {
        ServiceVenue venue = catalogService.venue(id)
                .orElseThrow(() -> new ReservationException("That venue could not be found"));
        User user = requireUser(authentication);
        if (!model.containsAttribute("arrangementRequest")) {
            ArrangementRequest request = new ArrangementRequest();
            request.setVenueId(id);
            request.setAttendees(1);
            request.setPaymentMethod(PaymentMethod.CASH);
            model.addAttribute("arrangementRequest", request);
        }
        populate(model, venue, user);
        return "venues/arrange";
    }

    @PostMapping(path = "/venues/{id}/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ArrangementPreview preview(
            @PathVariable Long id,
            @Valid @ModelAttribute ArrangementRequest arrangementRequest,
            Authentication authentication
    ) {
        arrangementRequest.setVenueId(id);
        arrangementRequest.setBookingSource("FORM");
        return reservationService.preview(requireUser(authentication), arrangementRequest);
    }

    @PostMapping(path = "/venues/{id}/spin", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ArrangementCreateResponse spin(
            @PathVariable Long id,
            @Valid @ModelAttribute ArrangementRequest arrangementRequest,
            Authentication authentication
    ) {
        arrangementRequest.setVenueId(id);
        arrangementRequest.setBookingSource("FORM");
        return reservationService.spin(requireUser(authentication), arrangementRequest);
    }

    private void populate(Model model, ServiceVenue venue, User user) {
        List<ServiceType> types = new ArrayList<>();
        for (ServiceType type : ServiceType.values()) {
            if (type.allows(venue.getType())) {
                types.add(type);
            }
        }
        model.addAttribute("venue", venue);
        model.addAttribute("serviceTypes", types);
        model.addAttribute("packages", FuneralPackage.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("phoneRequired", user.getPhone() == null || user.getPhone().isBlank());
        model.addAttribute("extras", catalogService.extras());
    }

    private User requireUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
    }
}
