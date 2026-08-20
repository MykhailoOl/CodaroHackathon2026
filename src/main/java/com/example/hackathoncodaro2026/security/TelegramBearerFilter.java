package com.example.hackathoncodaro2026.security;

import com.example.hackathoncodaro2026.service.TelegramTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TelegramBearerFilter extends OncePerRequestFilter {

    private final TelegramTokenService telegramTokenService;
    private final UserDetailsService userDetailsService;

    public TelegramBearerFilter(TelegramTokenService telegramTokenService, UserDetailsService userDetailsService) {
        this.telegramTokenService = telegramTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (!path.startsWith("/api/telegram/")) {
            return true;
        }
        return path.equals("/api/telegram/token") || path.equals("/api/telegram/token/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            TelegramTokenService.Issued issued = telegramTokenService.resolve(header.substring(7).trim());
            if (issued != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails details = userDetailsService.loadUserByUsername(issued.username());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        details,
                        null,
                        details.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
