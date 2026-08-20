package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.LoginRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    public AuthController(UserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (!model.containsAttribute("registrationRequest")) {
            model.addAttribute("registrationRequest", new RegistrationRequest());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationRequest") RegistrationRequest registrationRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Authentication authentication
    ) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        if (!bindingResult.hasFieldErrors("username")
                && userService.existsByUsername(registrationRequest.getUsername())) {
            bindingResult.rejectValue("username", "duplicate", "This username is already taken");
            auditLogService.record(
                    "REGISTER",
                    auditLogService.sanitize(registrationRequest.getUsername()),
                    Role.USER.name(),
                    "USER",
                    null,
                    "REJECTED",
                    Map.of("reason", "DUPLICATE", "field", "username")
            );
        }
        if (!bindingResult.hasFieldErrors("email")
                && userService.existsByEmail(registrationRequest.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", "This email is already registered");
            auditLogService.record(
                    "REGISTER",
                    auditLogService.sanitize(registrationRequest.getUsername()),
                    Role.USER.name(),
                    "USER",
                    null,
                    "REJECTED",
                    Map.of("reason", "DUPLICATE", "field", "email")
            );
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(registrationRequest);
        } catch (DuplicateUserException ex) {
            bindingResult.rejectValue(ex.getField(), "duplicate", ex.getMessage());
            return "register";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Account created. Sign in to arrange a ceremony.");
        return "redirect:/login";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
