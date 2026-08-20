package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.Notification;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.NotificationType;
import com.example.hackathoncodaro2026.repository.NotificationRepository;
import com.example.hackathoncodaro2026.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public Notification create(User recipient, NotificationType type, String title, String message, Long reservationId) {
        if (recipient == null || type == null) {
            throw new ReservationException("That notice could not be saved");
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(clip(title, 120, "Notice"));
        notification.setMessage(clip(message, 500, "Your arrangement was updated."));
        notification.setReservationId(reservationId);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> findFor(User recipient) {
        if (recipient == null || recipient.getId() == null) {
            return List.of();
        }
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipient.getId());
    }

    @Override
    public long unreadCount(User recipient) {
        if (recipient == null || recipient.getId() == null) {
            return 0L;
        }
        return notificationRepository.countByRecipient_IdAndReadFalse(recipient.getId());
    }

    @Override
    @Transactional
    public void markRead(User actor, Long notificationId) {
        if (actor == null || actor.getId() == null || notificationId == null) {
            throw new ReservationException("That notice could not be found");
        }
        Notification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, actor.getId())
                .orElseThrow(() -> new ReservationException("That notice could not be found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(User actor) {
        if (actor == null || actor.getId() == null) {
            return;
        }
        notificationRepository.markAllRead(actor.getId());
    }

    private String clip(String value, int max, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
