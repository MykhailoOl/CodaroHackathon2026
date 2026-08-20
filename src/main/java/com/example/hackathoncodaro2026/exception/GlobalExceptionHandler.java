package com.example.hackathoncodaro2026.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(basePackages = "com.example.hackathoncodaro2026.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(HttpServletRequest request) {
        log.warn("Access denied method={} path={} requestId={}", request.getMethod(), path(request), requestId());
        return "redirect:/";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("Data integrity violation method={} path={} requestId={}", request.getMethod(), path(request), requestId());
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/register")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That username or email is already registered.");
            return "redirect:/register";
        }
        if (uri != null && uri.startsWith("/admin/users")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That username or email is already registered.");
            return "redirect:/admin/users";
        }
        if (uri != null && uri.startsWith("/profile")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That email is already registered.");
            return "redirect:/profile";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "That change could not be saved.");
        return "redirect:/";
    }

    @ExceptionHandler(DuplicateUserException.class)
    public String handleDuplicate(DuplicateUserException exception, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn(
                "Duplicate user field={} method={} path={} requestId={}",
                exception.getField(),
                request.getMethod(),
                path(request),
                requestId()
        );
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/register")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That username or email is already registered.");
            return "redirect:/register";
        }
        if (uri != null && uri.startsWith("/admin/users")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That username or email is already registered.");
            return "redirect:/admin/users";
        }
        if (uri != null && uri.startsWith("/profile")) {
            redirectAttributes.addFlashAttribute("errorMessage", "That email is already registered.");
            return "redirect:/profile";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "That change could not be saved.");
        return "redirect:/";
    }

    @ExceptionHandler(ReservationException.class)
    public String handleReservation(
            ReservationException exception,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        log.warn(
                "Reservation rejected field={} method={} path={} requestId={}",
                exception.getField(),
                request.getMethod(),
                path(request),
                requestId()
        );
        redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        return "redirect:/reservations";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        if (exception instanceof NoResourceFoundException) {
            log.warn("Resource not found method={} path={} requestId={}", request.getMethod(), path(request), requestId());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("errorMessage", "The page you wanted is not available.");
            return "error";
        }
        log.error("Unhandled exception method={} path={} requestId={}", request.getMethod(), path(request), requestId(), exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        model.addAttribute("errorMessage", "The page you wanted is not available.");
        return "error";
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        int query = uri.indexOf('?');
        return query >= 0 ? uri.substring(0, query) : uri;
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? "" : value;
    }
}
