package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.voice.VoiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceInviteService {

    private final ReservationRepository reservationRepository;
    private final VoiceProperties properties;

    public VoiceInviteService(
            ReservationRepository reservationRepository,
            VoiceProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.properties = properties;
    }

    public Reservation requireByToken(String token) {
        return reservationRepository.findByInviteToken(token)
                .orElseThrow(() -> new ReservationException("That invite link is not valid."));
    }

    @Transactional
    public Reservation openInvite(String token) {
        return requireByToken(token);
    }

    public String googleCalendarUrl(Reservation reservation) {
        return GoogleCalendarLinks.templateUrl(reservation, properties.getTimezone());
    }

    public String inviteUrl(String token) {
        return trimSlash(properties.getPublicBaseUrl()) + "/voice/invite/" + token;
    }

    private String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
