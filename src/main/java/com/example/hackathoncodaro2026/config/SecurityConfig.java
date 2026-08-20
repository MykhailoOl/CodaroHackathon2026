package com.example.hackathoncodaro2026.config;

import com.example.hackathoncodaro2026.security.TelegramBearerFilter;
import com.example.hackathoncodaro2026.service.TelegramTokenService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public TelegramBearerFilter telegramBearerFilter(
            TelegramTokenService telegramTokenService,
            UserDetailsService userDetailsService
    ) {
        return new TelegramBearerFilter(telegramTokenService, userDetailsService);
    }

    @Bean
    public FilterRegistrationBean<TelegramBearerFilter> telegramBearerFilterRegistration(TelegramBearerFilter telegramBearerFilter) {
        FilterRegistrationBean<TelegramBearerFilter> registration = new FilterRegistrationBean<>(telegramBearerFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuditAuthenticationSuccessHandler auditAuthenticationSuccessHandler,
            AuditAuthenticationFailureHandler auditAuthenticationFailureHandler,
            AuditLogoutSuccessHandler auditLogoutSuccessHandler,
            TelegramBearerFilter telegramBearerFilter
    ) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/register",
                                "/login",
                                "/h2-console/**",
                                "/h2-launch",
                                "/error",
                                "/api/telegram/token",
                                "/api/voice/**",
                                "/voice/invite/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/manager/**").hasAnyRole("ADMIN", "MANAGER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(auditAuthenticationSuccessHandler)
                        .failureHandler(auditAuthenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(auditLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/telegram/**", "/api/voice/**"))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(assistantEntryPoint()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .addFilterBefore(telegramBearerFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint assistantEntryPoint() {
        AuthenticationEntryPoint json = (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in to arrange a ceremony.\"}"
            );
        };
        PathPatternRequestMatcher.Builder matcher = PathPatternRequestMatcher.withDefaults();
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(json, matcher.matcher("/api/reservation-assistant/**"))
                .addEntryPointFor(json, matcher.matcher("/api/telegram/**"))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .build();
    }
}
