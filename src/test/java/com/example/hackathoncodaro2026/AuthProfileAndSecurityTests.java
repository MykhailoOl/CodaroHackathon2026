package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.dto.AdminUserCreateRequest;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.ProfileUpdateRequest;
import com.example.hackathoncodaro2026.dto.RegistrationRequest;
import com.example.hackathoncodaro2026.exception.DuplicateUserException;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.UserRepository;
import com.example.hackathoncodaro2026.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerAndUpdateProfile() {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setUsername("family_one");
        registration.setEmail("family.one@example.com");
        registration.setFullName("Family One");
        registration.setPassword("Password1");
        registration.setConfirmPassword("Password1");
        User saved = userService.register(registration);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches("Password1", saved.getPassword())).isTrue();

        ProfileUpdateRequest profile = new ProfileUpdateRequest();
        profile.setFullName("Family One Updated");
        profile.setEmail("family.one.updated@example.com");
        profile.setPhone("+48 555 010 011");
        userService.updateProfile(saved, profile);
        User reloaded = userRepository.findByUsernameIgnoreCase("family_one").orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Family One Updated");
        assertThat(reloaded.getPhone()).isEqualTo("+48 555 010 011");
    }

    @Test
    void duplicateEmailIsRejected() {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setUsername("dup_mail");
        registration.setEmail("admin@everrest.example");
        registration.setFullName("Dup Mail");
        registration.setPassword("Password1");
        registration.setConfirmPassword("Password1");
        assertThatThrownBy(() -> userService.register(registration))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void adminCanCreateFamilyAndManagerOnly() {
        AdminUserCreateRequest manager = staff("desk_mgr", "desk.mgr@example.com", Role.MANAGER);
        assertThat(userService.createStaff(manager).getRole()).isEqualTo(Role.MANAGER);
        AdminUserCreateRequest family = staff("desk_family", "desk.family@example.com", Role.USER);
        assertThat(userService.createStaff(family).getRole()).isEqualTo(Role.USER);
        AdminUserCreateRequest admin = staff("desk_admin", "desk.admin@example.com", Role.ADMIN);
        assertThatThrownBy(() -> userService.createStaff(admin)).isInstanceOf(DuplicateUserException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void familyCannotOpenStaffOrQueue() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/manager/reservations").with(user("route_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void coachRoutesAreGone() throws Exception {
        mockMvc.perform(get("/coach/offerings"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/coach/sessions"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/facilities"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/resources/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void csrfIsRequiredForStaffCreate() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .param("username", "no_csrf")
                        .param("email", "no.csrf@example.com")
                        .param("fullName", "No Csrf")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password1")
                        .param("role", "MANAGER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void pagesIncludeEvelynWidget() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ask Evelyn")))
                .andExpect(content().string(containsString("everrest-assistant")))
                .andExpect(content().string(not(containsString("Courtly"))))
                .andExpect(content().string(not(containsString("Cora"))));
    }

    @Test
    void anonymousAssistantApiReturnsJson() throws Exception {
        mockMvc.perform(get("/api/reservation-assistant/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("UNAUTHENTICATED")));
    }

    @Test
    void loginRedirectsAnonymousHome() throws Exception {
        mockMvc.perform(get("/homes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private AdminUserCreateRequest staff(String username, String email, Role role) {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName(username);
        request.setPassword("Password1");
        request.setConfirmPassword("Password1");
        request.setRole(role);
        return request;
    }
}
