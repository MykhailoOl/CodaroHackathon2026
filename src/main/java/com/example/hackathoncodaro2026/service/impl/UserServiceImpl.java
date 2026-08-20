package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.ProfileUpdateRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public User register(RegistrationRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            auditLogService.record(
                    "REGISTER",
                    username,
                    Role.USER.name(),
                    "USER",
                    null,
                    "REJECTED",
                    Map.of("reason", "DUPLICATE", "field", "username")
            );
            throw new DuplicateUserException("username", "This username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            auditLogService.record(
                    "REGISTER",
                    username,
                    Role.USER.name(),
                    "USER",
                    null,
                    "REJECTED",
                    Map.of("reason", "DUPLICATE", "field", "email")
            );
            throw new DuplicateUserException("email", "This email is already registered");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        user.setRole(Role.USER);
        user.setEnabled(true);
        User saved = userRepository.save(user);
        auditLogService.record(
                "REGISTER",
                saved.getUsername(),
                Role.USER.name(),
                "USER",
                saved.getId(),
                "SUCCESS",
                Map.of()
        );
        return saved;
    }

    @Override
    @Transactional
    public User updateProfile(User user, ProfileUpdateRequest request) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new DuplicateUserException("username", "Signed-in user was not found"));
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, managed.getId())) {
            auditLogService.record(
                    managed,
                    "PROFILE_UPDATE",
                    "USER",
                    managed.getId(),
                    "REJECTED",
                    Map.of("reason", "DUPLICATE", "field", "email")
            );
            throw new DuplicateUserException("email", "This email is already registered");
        }
        managed.setFullName(request.getFullName().trim());
        managed.setEmail(email);
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            managed.setPhone(request.getPhone().trim());
        } else {
            managed.setPhone(null);
        }
        boolean passwordChanged = false;
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), managed.getPassword())) {
                auditLogService.record(
                        managed,
                        "PASSWORD_CHANGE",
                        "USER",
                        managed.getId(),
                        "FAILURE",
                        Map.of("reason", "CURRENT_PASSWORD_INVALID")
                );
                throw new DuplicateUserException("currentPassword", "Current password is incorrect");
            }
            managed.setPassword(passwordEncoder.encode(request.getNewPassword()));
            passwordChanged = true;
        }
        User saved = userRepository.save(managed);
        auditLogService.record(
                saved,
                "PROFILE_UPDATE",
                "USER",
                saved.getId(),
                "SUCCESS",
                Map.of("passwordChanged", passwordChanged)
        );
        if (passwordChanged) {
            auditLogService.record(
                    saved,
                    "PASSWORD_CHANGE",
                    "USER",
                    saved.getId(),
                    "SUCCESS",
                    Map.of("changed", true)
            );
        }
        return saved;
    }

    @Override
    @Transactional
    public User updatePhone(User user, String phone) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new DuplicateUserException("username", "Signed-in user was not found"));
        if (phone == null || phone.isBlank()) {
            throw new DuplicateUserException("phone", "Phone is required");
        }
        managed.setPhone(phone.trim());
        return userRepository.save(managed);
    }

    @Override
    @Transactional
    public User createStaff(AdminUserCreateRequest request) {
        Role role = request.getRole();
        if (role != Role.USER && role != Role.MANAGER) {
            throw new DuplicateUserException("role", "Staff accounts may be Family or Manager");
        }
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            auditStaffRejected(username, role, "username");
            throw new DuplicateUserException("username", "This username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            auditStaffRejected(username, role, "email");
            throw new DuplicateUserException("email", "This email is already registered");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        user.setRole(role);
        user.setEnabled(true);
        User saved = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("createdUsername", saved.getUsername());
        details.put("createdRole", saved.getRole().name());
        auditLogService.record(
                "STAFF_CREATE",
                auditLogService.currentActor(),
                auditLogService.currentRole(),
                "USER",
                saved.getId(),
                "SUCCESS",
                details
        );
        return saved;
    }

    private void auditStaffRejected(String username, Role role, String field) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "DUPLICATE");
        details.put("field", field);
        details.put("createdUsername", username);
        details.put("createdRole", role == null ? "" : role.name());
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

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return username != null && userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && userRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Override
    public Optional<User> findById(Long id) {
        return id == null ? Optional.empty() : userRepository.findById(id);
    }
}
