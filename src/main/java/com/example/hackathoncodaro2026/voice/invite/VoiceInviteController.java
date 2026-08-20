package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class VoiceInviteController {

    private final VoiceInviteService voiceInviteService;

    public VoiceInviteController(VoiceInviteService voiceInviteService) {
        this.voiceInviteService = voiceInviteService;
    }

    @GetMapping("/voice/invite/{token}")
    public String form(@PathVariable String token, Model model) {
        try {
            Reservation reservation = voiceInviteService.openInvite(token);
            model.addAttribute("reservation", reservation);
            model.addAttribute("token", token);
            model.addAttribute("googleCalendarUrl", voiceInviteService.googleCalendarUrl(reservation));
            return "voice/invite";
        } catch (ReservationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "error";
        }
    }
}
