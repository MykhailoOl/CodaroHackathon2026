package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HackathonCodaro2026ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
        assertThat(userRepository.existsByUsernameIgnoreCase("admin")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("manager")).isTrue();
        assertThat(userRepository.findByRoleOrderByFullNameAsc(Role.MANAGER)).isNotEmpty();
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void registerPageIsPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    void homeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void registerCreatesUserAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "familyone")
                        .param("email", "family@example.com")
                        .param("fullName", "Family One")
                        .param("phone", "")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertThat(userRepository.existsByUsernameIgnoreCase("familyone")).isTrue();
    }

    @Test
    void adminCanLogIn() throws Exception {
        mockMvc.perform(formLogin().user("admin").password("Admin123!"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void invalidLoginStaysAnonymous() throws Exception {
        mockMvc.perform(formLogin().user("admin").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void homesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/homes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminCanOpenHomesAndHistory() throws Exception {
        mockMvc.perform(get("/homes"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk());
    }

    @Test
    void occupancyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/occupancy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminCanOpenOccupancyAndProfile() throws Exception {
        mockMvc.perform(get("/occupancy"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk());
    }

    @Test
    void managerCanLogIn() throws Exception {
        mockMvc.perform(formLogin().user("manager").password("Manager123!"))
                .andExpect(authenticated().withUsername("manager"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanOpenStaffAndQueue() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/manager/reservations"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void managerCanOpenQueueButNotStaff() throws Exception {
        mockMvc.perform(get("/manager/reservations"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminStaffCreateFormHasNoPhotoUpload() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("type=\"file\""))))
                .andExpect(content().string(not(containsString("name=\"avatar\""))))
                .andExpect(content().string(not(containsString("multipart/form-data"))))
                .andExpect(content().string(not(containsString("Photo"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateStaffWithoutPhoto() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .param("username", "desk_lead")
                        .param("email", "desk.lead@example.com")
                        .param("fullName", "Desk Lead")
                        .param("phone", "+48 555 010 050")
                        .param("password", "Password1")
                        .param("confirmPassword", "Password1")
                        .param("role", "MANAGER")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(userRepository.existsByUsernameIgnoreCase("desk_lead")).isTrue();
        assertThat(userRepository.findByUsernameIgnoreCase("desk_lead").orElseThrow().getRole())
                .isEqualTo(Role.MANAGER);
    }
}
