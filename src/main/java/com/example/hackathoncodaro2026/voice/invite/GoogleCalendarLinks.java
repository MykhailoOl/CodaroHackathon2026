package com.example.hackathoncodaro2026.voice.invite;

import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

public final class GoogleCalendarLinks {

    private static final DateTimeFormatter GOOGLE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private GoogleCalendarLinks() {
    }

    public static String templateUrl(Reservation reservation, String timezone) {
        String tz = blank(timezone) ? "Europe/Warsaw" : timezone;
        String dates = GOOGLE.format(reservation.getStartAt()) + "/" + GOOGLE.format(reservation.getEndAt());
        return "https://calendar.google.com/calendar/render"
                + "?action=TEMPLATE"
                + "&text=" + encode(title(reservation))
                + "&dates=" + dates
                + "&ctz=" + tz
                + "&location=" + encode(location(reservation))
                + "&details=" + encode("EverRest ceremony reminder. A director will confirm the arrangement.");
    }

    public static String title(Reservation reservation) {
        if (reservation.getVenue() == null || blank(reservation.getVenue().getName())) {
            return "EverRest ceremony";
        }
        return "EverRest — " + reservation.getVenue().getName();
    }

    public static String location(Reservation reservation) {
        ServiceVenue venue = reservation.getVenue();
        if (venue == null) {
            return "EverRest Warsaw";
        }
        FuneralHome home = venue.getFuneralHome();
        if (home == null || blank(home.getName())) {
            return venue.getName();
        }
        return venue.getName() + ", " + home.getName();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
