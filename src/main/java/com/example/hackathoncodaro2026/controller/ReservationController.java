package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.CancellationReason;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class ReservationController {

    private final ReservationService reservationService;
    private final UserService userService;
    private final CatalogService catalogService;

    public ReservationController(
            ReservationService reservationService,
            UserService userService,
            CatalogService catalogService
    ) {
        this.reservationService = reservationService;
        this.userService = userService;
        this.catalogService = catalogService;
    }

    @GetMapping("/reservations")
    public String history(Authentication authentication, Model model) {
        User user = requireUser(authentication);
        List<Reservation> items = user.getRole() == Role.ADMIN
                ? reservationService.findAll()
                : reservationService.findForUser(user);
        model.addAttribute("reservations", items);
        model.addAttribute("cancelReasons", CancellationReason.values());
        return "reservations/history";
    }

    @GetMapping("/reservations/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User user = requireUser(authentication);
        Reservation reservation = reservationService.findWithDetails(id)
                .orElseThrow(() -> new ReservationException("That arrangement could not be found"));
        if (!reservationService.canEdit(user, reservation)) {
            throw new ReservationException("Only a pending arrangement can be updated");
        }
        if (!model.containsAttribute("arrangementRequest")) {
            ArrangementRequest request = new ArrangementRequest();
            request.setVenueId(reservation.getVenue().getId());
            request.setServiceType(reservation.getServiceType());
            request.setFuneralPackage(reservation.getFuneralPackage());
            request.setDeceasedFullName(reservation.getDeceasedFullName());
            request.setDateOfBirth(reservation.getDateOfBirth());
            request.setDateOfDeath(reservation.getDateOfDeath());
            request.setAttendees(reservation.getAttendees());
            request.setPaymentMethod(reservation.getPaymentMethod());
            request.setNote(reservation.getNote());
            List<Long> extraIds = new ArrayList<>();
            reservation.getExtras().forEach(line -> {
                if (line.getItem() != null) {
                    extraIds.add(line.getItem().getId());
                }
            });
            request.setExtraIds(extraIds);
            model.addAttribute("arrangementRequest", request);
        }
        populateEdit(model, reservation, user);
        return "reservations/edit";
    }

    @PostMapping("/reservations/{id}/edit")
    public String edit(
            @PathVariable Long id,
            @Valid @ModelAttribute("arrangementRequest") ArrangementRequest arrangementRequest,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = requireUser(authentication);
        Reservation reservation = reservationService.findWithDetails(id)
                .orElseThrow(() -> new ReservationException("That arrangement could not be found"));
        arrangementRequest.setVenueId(reservation.getVenue().getId());
        arrangementRequest.setFuneralPackage(reservation.getFuneralPackage());
        if (bindingResult.hasErrors()) {
            populateEdit(model, reservation, user);
            return "reservations/edit";
        }
        try {
            reservationService.update(user, id, arrangementRequest);
        } catch (ReservationException ex) {
            if (ex.getField() != null) {
                bindingResult.rejectValue(ex.getField(), "invalid", ex.getMessage());
            } else {
                bindingResult.reject("invalid", ex.getMessage());
            }
            populateEdit(model, reservation, user);
            return "reservations/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Arrangement updated.");
        return "redirect:/reservations";
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String otherNote,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        User user = requireUser(authentication);
        try {
            reservationService.cancel(user, id, reason, otherNote);
            redirectAttributes.addFlashAttribute("successMessage", "Arrangement cancelled.");
        } catch (ReservationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/reservations";
    }

    private void populateEdit(Model model, Reservation reservation, User user) {
        List<ServiceType> types = new ArrayList<>();
        for (ServiceType type : ServiceType.values()) {
            if (type.allows(reservation.getVenue().getType())) {
                types.add(type);
            }
        }
        model.addAttribute("reservation", reservation);
        model.addAttribute("venue", reservation.getVenue());
        model.addAttribute("serviceTypes", types);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("phoneRequired", user.getPhone() == null || user.getPhone().isBlank());
        model.addAttribute("extras", catalogService.extras());
    }

    private User requireUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
    }
}
