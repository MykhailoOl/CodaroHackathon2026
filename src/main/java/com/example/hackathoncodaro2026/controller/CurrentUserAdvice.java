package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.config.DomainProperties;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.service.NotificationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    private final UserService userService;
    private final NotificationService notificationService;
    private final DomainProperties domain;

    public CurrentUserAdvice(UserService userService, NotificationService notificationService,
                             DomainProperties domain) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.domain = domain;
    }

    @ModelAttribute("brand")
    public DomainProperties.Brand brand() {
        return domain.brand();
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userService.findByUsername(authentication.getName()).orElse(null);
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return 0L;
        }
        return notificationService.unreadCount(user);
    }
}
