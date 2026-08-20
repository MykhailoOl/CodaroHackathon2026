package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LoggingInvitationMailer implements InvitationMailer {

    public record SentInvite(String to, String summary) {
    }

    private static final Logger log = LoggerFactory.getLogger(LoggingInvitationMailer.class);
    private final CopyOnWriteArrayList<SentInvite> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendCalendarInvite(String toEmail, Reservation reservation, String icsBody) {
        String summary = reservation.getResource().getName() + " " + reservation.getStartAt();
        sent.add(new SentInvite(toEmail, summary));
        log.info("Calendar invite placeholder to={} chars={} summary={}", mask(toEmail), icsBody.length(), summary);
    }

    public List<SentInvite> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    private String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        return "***@" + email.substring(email.indexOf('@') + 1);
    }
}
