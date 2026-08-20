package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarLinksTest {

    @Test
    void buildsAGoogleTemplateReminderForTheChapel() {
        FuneralHome home = new FuneralHome();
        home.setName("EverRest Warsaw");
        ServiceVenue venue = new ServiceVenue();
        venue.setName("Willow Chapel");
        venue.setFuneralHome(home);
        Reservation reservation = new Reservation();
        reservation.setVenue(venue);
        reservation.setStartAt(LocalDateTime.of(2026, 8, 21, 9, 0));
        reservation.setEndAt(LocalDateTime.of(2026, 8, 21, 11, 0));

        assertThat(GoogleCalendarLinks.title(reservation)).isEqualTo("EverRest — Willow Chapel");
        assertThat(GoogleCalendarLinks.location(reservation)).isEqualTo("Willow Chapel, EverRest Warsaw");
        assertThat(GoogleCalendarLinks.templateUrl(reservation, "Europe/Warsaw"))
                .contains("action=TEMPLATE")
                .contains("ctz=Europe/Warsaw")
                .contains("20260821T090000/20260821T110000")
                .doesNotContain("Courtly");
    }
}
