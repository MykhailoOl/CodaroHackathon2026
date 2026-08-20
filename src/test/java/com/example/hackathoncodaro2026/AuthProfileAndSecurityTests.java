package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.ProfileUpdateRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.dto.ReservationRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.repository.UserSportLevelRepository;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthProfileAndSecurityTests {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SportResourceRepository sportResourceRepository;

    @Autowired
    private UserSportLevelRepository userSportLevelRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerHashesPasswordAndRejectsDuplicates() {
        User saved = register("hash_player", "hash.player@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("Password1");
        assertThat(passwordEncoder.matches("Password1", saved.getPassword())).isTrue();
        assertThatThrownBy(() -> register("hash_player", "other.hash@example.com"))
                .isInstanceOf(DuplicateUserException.class)
                .extracting(ex -> ((DuplicateUserException) ex).getField())
                .isEqualTo("username");
        assertThatThrownBy(() -> register("hash_player_two", "hash.player@example.com"))
                .isInstanceOf(DuplicateUserException.class)
                .extracting(ex -> ((DuplicateUserException) ex).getField())
                .isEqualTo("email");
    }

    @Test
    void registerFormRejectsMismatchedPasswordsAndShortUsername() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "ab")
                        .param("email", "short.user@example.com")
                        .param("fullName", "Short User")
                        .param("phone", "")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("username")));
        mockMvc.perform(post("/register")
                        .param("username", "mismatch_user")
                        .param("email", "mismatch.user@example.com")
                        .param("fullName", "Mismatch User")
                        .param("phone", "")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password2")
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(userRepository.existsByUsernameIgnoreCase("mismatch_user")).isFalse();
        assertThat(userRepository.existsByUsernameIgnoreCase("ab")).isFalse();
    }

    @Test
    void profileUpdatesContactAndKeepsUsername() {
        User player = register("profile_edit", "profile.edit@example.com");
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        form.setFullName("Edited Name");
        form.setEmail("profile.edit.new@example.com");
        form.setPhone("+48 555 010 111");
        User updated = userService.updateProfile(player, form);
        assertThat(updated.getUsername()).isEqualTo("profile_edit");
        assertThat(updated.getFullName()).isEqualTo("Edited Name");
        assertThat(updated.getEmail()).isEqualTo("profile.edit.new@example.com");
        assertThat(updated.getPhone()).isEqualTo("+48 555 010 111");
        User other = register("profile_other", "profile.other@example.com");
        ProfileUpdateRequest clash = new ProfileUpdateRequest();
        clash.setFullName("Other Name");
        clash.setEmail("profile.edit.new@example.com");
        clash.setPhone("+48 555 010 112");
        assertThatThrownBy(() -> userService.updateProfile(other, clash))
                .isInstanceOf(DuplicateUserException.class)
                .extracting(ex -> ((DuplicateUserException) ex).getField())
                .isEqualTo("email");
    }

    @Test
    void passwordChangeRequiresCurrentPassword() {
        User player = register("pwd_change", "pwd.change@example.com");
        String previous = player.getPassword();
        ProfileUpdateRequest wrong = new ProfileUpdateRequest();
        wrong.setFullName(player.getFullName());
        wrong.setEmail(player.getEmail());
        wrong.setPhone(player.getPhone());
        wrong.setCurrentPassword("WrongPass1");
        wrong.setNewPassword("Password2");
        wrong.setConfirmPassword("Password2");
        assertThatThrownBy(() -> userService.updateProfile(player, wrong))
                .isInstanceOf(DuplicateUserException.class)
                .extracting(ex -> ((DuplicateUserException) ex).getField())
                .isEqualTo("currentPassword");
        ProfileUpdateRequest ok = new ProfileUpdateRequest();
        ok.setFullName(player.getFullName());
        ok.setEmail(player.getEmail());
        ok.setPhone(player.getPhone());
        ok.setCurrentPassword("Password1");
        ok.setNewPassword("Password2");
        ok.setConfirmPassword("Password2");
        User updated = userService.updateProfile(player, ok);
        assertThat(updated.getPassword()).isNotEqualTo(previous);
        assertThat(passwordEncoder.matches("Password2", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Password1", updated.getPassword())).isFalse();
    }

    @Test
    void profilePersistsValidSportLevelsAndIgnoresCrossSportCodes() {
        User player = register("levels_player", "levels.player@example.com");
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        form.setFullName(player.getFullName());
        form.setEmail(player.getEmail());
        form.setPhone(player.getPhone());
        form.getSportLevels().put(ResourceType.CHAPEL.name(), "CATHOLIC");
        form.getSportLevels().put(ResourceType.TRANSPORT.name(), "DRIVER");
        form.getSportLevels().put(ResourceType.REPATRIATION.name(), "CATHOLIC");
        userService.updateProfile(player, form);
        assertThat(userSportLevelRepository.findByUser_IdAndSportType(player.getId(), ResourceType.CHAPEL)
                .orElseThrow()
                .getSkillLevel()).isEqualTo("CATHOLIC");
        assertThat(userSportLevelRepository.findByUser_IdAndSportType(player.getId(), ResourceType.TRANSPORT)
                .orElseThrow()
                .getSkillLevel()).isEqualTo("DRIVER");
        assertThat(userSportLevelRepository.findByUser_IdAndSportType(player.getId(), ResourceType.REPATRIATION)).isEmpty();
    }

    @Test
    void bookingWithoutPhoneFailsWhenProfileHasNone() {
        RegistrationRequest request = new RegistrationRequest();
        request.setUsername("silent_phone");
        request.setEmail("silent.phone@example.com");
        request.setFullName("Silent Phone");
        request.setPassword("Password1");
        request.setConfirmPassword("Password1");
        User player = userService.register(request);
        SportResource court = exclusiveCourt();
        ReservationRequest booking = booking(court, LocalDate.now(WARSAW).plusDays(41));
        assertThatThrownBy(() -> reservationService.create(player, booking))
                .isInstanceOf(ReservationException.class)
                .hasMessageContaining("Phone");
    }

    @Test
    void adminCreatesPlayerAndCoachAndRejectsAdminRole() {
        AdminUserCreateRequest player = staff("created_player", "created.player@example.com", Role.USER);
        assertThat(userService.createStaff(player).getRole()).isEqualTo(Role.USER);
        AdminUserCreateRequest coach = staff("created_coach", "created.coach@example.com", Role.COACH);
        assertThat(userService.createStaff(coach).getRole()).isEqualTo(Role.COACH);
        AdminUserCreateRequest admin = staff("created_admin", "created.admin@example.com", Role.ADMIN);
        assertThatThrownBy(() -> userService.createStaff(admin))
                .isInstanceOf(DuplicateUserException.class)
                .extracting(ex -> ((DuplicateUserException) ex).getField())
                .isEqualTo("role");
    }

    @Test
    void profilePageKeepsUsernameReadOnly() throws Exception {
        register("readonly_name", "readonly.name@example.com");
        mockMvc.perform(get("/profile").with(user("readonly_name").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("readonly_name")))
                .andExpect(content().string(containsString("readonly")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanOpenHomeAndCoachCannotOpenStaff() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        userService.createStaff(staff("gate_coach", "gate.coach@example.com", Role.COACH));
        mockMvc.perform(get("/admin/users").with(user("gate_coach").roles("COACH")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/manager/reservations").with(user("gate_coach").roles("COACH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotOpenStaffQueueOrCoachPages() throws Exception {
        register("route_user", "route.user@example.com");
        mockMvc.perform(get("/admin/users").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/manager/reservations").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/coach/offerings").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/coach/sessions").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users")
                        .param("username", "sneaky_mgr")
                        .param("email", "sneaky.mgr@example.com")
                        .param("fullName", "Sneaky")
                        .param("phone", "+48 555 010 113")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password1")
                        .param("role", "MANAGER")
                        .with(user("route_user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(userRepository.existsByUsernameIgnoreCase("sneaky_mgr")).isFalse();
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCannotOpenAdminStaff() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/coach/offerings"))
                .andExpect(status().isForbidden());
    }

    private User register(String username, String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName("Player " + username);
        request.setPhone("+48 555 010 114");
        request.setPassword("Password1");
        request.setConfirmPassword("Password1");
        return userService.register(request);
    }

    private AdminUserCreateRequest staff(String username, String email, Role role) {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName("Staff " + username);
        request.setPhone("+48 555 010 115");
        request.setPassword("Password1");
        request.setConfirmPassword("Password1");
        request.setRole(role);
        return request;
    }

    private SportResource exclusiveCourt() {
        return sportResourceRepository.findAll().stream()
                .filter(resource -> resource.getCapacity() == 1 && resource.isEnabled())
                .findFirst()
                .orElseThrow();
    }

    private ReservationRequest booking(SportResource resource, LocalDate date) {
        ReservationRequest request = new ReservationRequest();
        request.setResourceId(resource.getId());
        request.setDate(date);
        request.setStartTime(resource.getOpeningTime());
        request.setDurationHours(1);
        request.setPaymentMethod(PaymentMethod.CASH);
        if (resource.requiresPartySize()) {
            request.setPartySize(resource.getMinPartySize());
        }
        return request;
    }
}
