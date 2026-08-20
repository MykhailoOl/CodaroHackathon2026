package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ProfileUpdateRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        User user = requireUser(authentication);
        populate(model, user);
        if (!model.containsAttribute("profileUpdateRequest")) {
            ProfileUpdateRequest form = new ProfileUpdateRequest();
            form.setFullName(user.getFullName());
            form.setEmail(user.getEmail());
            form.setPhone(user.getPhone());
            model.addAttribute("profileUpdateRequest", form);
        }
        return "profile/edit";
    }

    @PostMapping("/profile")
    public String update(
            @Valid @ModelAttribute("profileUpdateRequest") ProfileUpdateRequest profileUpdateRequest,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = requireUser(authentication);
        populate(model, user);
        if (bindingResult.hasErrors()) {
            return "profile/edit";
        }
        try {
            userService.updateProfile(user, profileUpdateRequest);
        } catch (DuplicateUserException ex) {
            bindingResult.rejectValue(ex.getField(), "invalid", ex.getMessage());
            return "profile/edit";
        } catch (ReservationException ex) {
            if (ex.getField() != null) {
                bindingResult.rejectValue(ex.getField(), "invalid", ex.getMessage());
            } else {
                bindingResult.reject("invalid", ex.getMessage());
            }
            return "profile/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Profile saved.");
        return "redirect:/profile";
    }

    private void populate(Model model, User user) {
        model.addAttribute("username", user.getUsername());
    }

    private User requireUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new DuplicateUserException("username", "Signed-in user was not found"));
    }
}
