package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.voice.VoiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class VoiceInviteService {

    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneId.of("Europe/Warsaw"));

    private final ReservationRepository reservationRepository;
    private final InvitationMailer invitationMailer;
    private final VoiceProperties properties;

    public VoiceInviteService(
            ReservationRepository reservationRepository,
            InvitationMailer invitationMailer,
            VoiceProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.invitationMailer = invitationMailer;
        this.properties = properties;
    }

    public Reservation requireByToken(String token) {
        return reservationRepository.findByInviteToken(token)
                .orElseThrow(() -> new ReservationException("That invite link is not valid."));
    }

    @Transactional
    public Reservation attachEmail(String token, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReservationException("Enter a valid email address.");
        }
        Reservation reservation = requireByToken(token);
        reservation.setInviteEmail(normalized);
        reservation.setInviteSentAt(Instant.now());
        Reservation saved = reservationRepository.save(reservation);
        invitationMailer.sendCalendarInvite(normalized, saved, toIcs(saved));
        return saved;
    }

    public String toIcs(Reservation reservation) {
        String uid = (reservation.getInviteToken() == null ? UUID.randomUUID().toString() : reservation.getInviteToken())
                + "@courtly.local";
        String venue = reservation.getResource().getName();
        if (reservation.getResource().getFacility() != null) {
            venue = venue + ", " + reservation.getResource().getFacility().getName();
        }
        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Courtly//Voice booking//EN
                BEGIN:VEVENT
                UID:%s
                DTSTAMP:%s
                DTSTART;TZID=Europe/Warsaw:%s
                DTEND;TZID=Europe/Warsaw:%s
                SUMMARY:Courtly — %s
                LOCATION:%s
                DESCRIPTION:Booked by phone. Pay at the facility.
                END:VEVENT
                END:VCALENDAR
                """.formatted(
                uid,
                ICS.format(Instant.now()),
                ICS.format(reservation.getStartAt().atZone(ZoneId.of(properties.getTimezone())).toInstant()),
                ICS.format(reservation.getEndAt().atZone(ZoneId.of(properties.getTimezone())).toInstant()),
                reservation.getResource().getName(),
                venue
        ).replace("\n", "\r\n");
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
