package com.example.hackathoncodaro2026.intent.service;

import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.service.ReservationService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class IntentBookingService {

    private final ReservationService reservationService;

    public IntentBookingService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public Reservation book(
            User user,
            Long resourceId,
            LocalDateTime start,
            LocalDateTime end,
            Integer partySize,
            PaymentMethod paymentMethod
    ) {
        if (resourceId == null) {
            throw new ReservationException("resourceId", "A resource must be chosen");
        }
        if (start == null || end == null) {
            throw new ReservationException("start", "Start and end time are required");
        }
        if (!end.isAfter(start)) {
            throw new ReservationException("end", "End time must be after start time");
        }
        if (paymentMethod == null) {
            throw new ReservationException("paymentMethod", "Choose a payment method");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes % 60 != 0) {
            throw new ReservationException("end", "Duration must be a whole number of hours");
        }
        int hours = (int) (minutes / 60);

        ReservationRequest request = new ReservationRequest();
        request.setResourceId(resourceId);
        request.setDate(start.toLocalDate());
        request.setStartTime(start.toLocalTime());
        request.setDurationHours(hours);
        request.setPartySize(partySize);
        request.setPaymentMethod(paymentMethod);

        return reservationService.create(user, request);
    }
}
