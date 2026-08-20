package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AdminUserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    public AdminUserController(UserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/users")
    public String form(Model model) {
        if (!model.containsAttribute("adminUserCreateRequest")) {
            AdminUserCreateRequest request = new AdminUserCreateRequest();
            request.setRole(Role.MANAGER);
            model.addAttribute("adminUserCreateRequest", request);
        }
        model.addAttribute("roles", new Role[]{Role.USER, Role.MANAGER});
        return "admin/users";
    }

    @PostMapping("/admin/users")
    public String create(
            @Valid @ModelAttribute("adminUserCreateRequest") AdminUserCreateRequest adminUserCreateRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        model.addAttribute("roles", new Role[]{Role.USER, Role.MANAGER});
        if (adminUserCreateRequest.getRole() == Role.ADMIN) {
            bindingResult.rejectValue("role", "invalid", "Admins can create family or manager accounts only");
        }
        if (!bindingResult.hasFieldErrors("username")
                && userService.existsByUsername(adminUserCreateRequest.getUsername())) {
            bindingResult.rejectValue("username", "duplicate", "This username is already taken");
            auditStaffRejected(adminUserCreateRequest, "username");
        }
        if (!bindingResult.hasFieldErrors("email")
                && userService.existsByEmail(adminUserCreateRequest.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", "This email is already registered");
            auditStaffRejected(adminUserCreateRequest, "email");
        }
        if (bindingResult.hasErrors()) {
            return "admin/users";
        }
        try {
            userService.createStaff(adminUserCreateRequest);
        } catch (DuplicateUserException ex) {
            bindingResult.rejectValue(ex.getField(), "invalid", ex.getMessage());
            return "admin/users";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Account created.");
        return "redirect:/admin/users";
    }

    private void auditStaffRejected(AdminUserCreateRequest request, String field) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "DUPLICATE");
        details.put("field", field);
        details.put("createdUsername", auditLogService.sanitize(request.getUsername()));
        details.put("createdRole", request.getRole() == null ? "" : request.getRole().name());
        auditLogService.record(
                "STAFF_CREATE",
                auditLogService.currentActor(),
                auditLogService.currentRole(),
                "USER",
                null,
                "REJECTED",
                details
        );
    }
}
