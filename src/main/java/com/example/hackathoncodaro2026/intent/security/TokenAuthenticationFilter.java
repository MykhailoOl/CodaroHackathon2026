package com.example.hackathoncodaro2026.intent.security;

import com.example.hackathoncodaro2026.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_TOKEN_PATH = "/api/auth/token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final CustomUserDetailsService userDetailsService;

    public TokenAuthenticationFilter(TokenService tokenService, CustomUserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAuthTokenRequest(request) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response, "Missing bearer token");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Optional<TokenService.VerifiedToken> verified = tokenService.verify(token);
        if (verified.isEmpty()) {
            reject(response, "Invalid or expired token");
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(verified.get().username());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            reject(response, "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private boolean isAuthTokenRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return AUTH_TOKEN_PATH.equals(uri);
    }
}
