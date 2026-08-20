package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VoiceInviteController {

    private final VoiceInviteService voiceInviteService;

    public VoiceInviteController(VoiceInviteService voiceInviteService) {
        this.voiceInviteService = voiceInviteService;
    }

    @GetMapping("/voice/invite/{token}")
    public String form(@PathVariable String token, Model model) {
        try {
            Reservation reservation = voiceInviteService.requireByToken(token);
            model.addAttribute("reservation", reservation);
            model.addAttribute("token", token);
            return "voice/invite";
        } catch (ReservationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "error";
        }
    }

    @PostMapping("/voice/invite/{token}")
    public String submit(
            @PathVariable String token,
            @RequestParam String email,
            RedirectAttributes redirectAttributes
    ) {
        try {
            voiceInviteService.attachEmail(token, email);
            redirectAttributes.addFlashAttribute("successMessage", "Invitation sent. You can also download the calendar file.");
        } catch (ReservationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/voice/invite/" + token;
    }

    @GetMapping("/voice/invite/{token}/calendar.ics")
    public ResponseEntity<String> calendar(@PathVariable String token) {
        try {
            Reservation reservation = voiceInviteService.requireByToken(token);
            String ics = voiceInviteService.toIcs(reservation);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=courtly-booking.ics")
                    .contentType(MediaType.parseMediaType("text/calendar"))
                    .body(ics);
        } catch (ReservationException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
