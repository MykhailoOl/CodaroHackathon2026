package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementPreview;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.PriceQuote;
import com.example.hackathoncodaro2026.dto.ReservationUpdateResult;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationService {

    ArrangementPreview preview(User user, ArrangementRequest request);

    PriceQuote quote(User user, ArrangementRequest request);

    Reservation create(User user, ArrangementRequest request);

    ArrangementCreateResponse spin(User user, ArrangementRequest request);

    ReservationUpdateResult update(User actor, Long reservationId, ArrangementRequest request);

    void cancel(User actor, Long reservationId);

    void cancel(User actor, Long reservationId, String reason);

    void cancel(User actor, Long reservationId, String reason, String otherNote);

    void confirm(User actor, Long reservationId);

    List<Reservation> findForUser(User user);

    List<Reservation> findAll();

    List<Reservation> findManagerQueue(LocalDate date);

    long countUpcomingActive(User user);

    int deleteEndedBefore(LocalDateTime cutoff);

    int deleteEndedOlderThanOneMonth();

    Optional<Reservation> findWithDetails(Long id);

    boolean canEdit(User actor, Reservation reservation);
}
