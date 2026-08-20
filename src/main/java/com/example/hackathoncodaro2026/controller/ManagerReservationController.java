package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.CancellationReason;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class ManagerReservationController {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ReservationService reservationService;
    private final UserService userService;

    public ManagerReservationController(ReservationService reservationService, UserService userService) {
        this.reservationService = reservationService;
        this.userService = userService;
    }

    @GetMapping("/manager/reservations")
    public String queue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication,
            Model model
    ) {
        requireUser(authentication);
        LocalDate selected = date == null ? LocalDate.now(WARSAW) : date;
        model.addAttribute("reservations", reservationService.findManagerQueue(selected));
        model.addAttribute("now", LocalDateTime.now(WARSAW));
        model.addAttribute("today", LocalDate.now(WARSAW));
        model.addAttribute("selectedDate", selected);
        model.addAttribute("cancelReasons", CancellationReason.values());
        return "manager/queue";
    }

    @PostMapping("/manager/reservations/{id}/confirm")
    public String confirm(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        User user = requireUser(authentication);
        try {
            reservationService.confirm(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Arrangement confirmed.");
        } catch (ReservationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectQueue(date);
    }

    @PostMapping("/manager/reservations/{id}/cancel")
    public String cancel(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
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
        return redirectQueue(date);
    }

    private String redirectQueue(LocalDate date) {
        LocalDate selected = date == null ? LocalDate.now(WARSAW) : date;
        return "redirect:/manager/reservations?date=" + selected;
    }

    private User requireUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
    }
}
