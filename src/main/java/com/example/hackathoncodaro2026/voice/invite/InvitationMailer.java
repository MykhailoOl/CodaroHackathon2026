package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.model.Reservation;

public interface InvitationMailer {

    void sendCalendarInvite(String toEmail, Reservation reservation, String icsBody);
}
