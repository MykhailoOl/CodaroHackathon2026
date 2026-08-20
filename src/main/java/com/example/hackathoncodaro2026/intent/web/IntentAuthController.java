package com.example.hackathoncodaro2026.intent.web;

import com.example.hackathoncodaro2026.intent.security.TokenService;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@RequestMapping("/api/auth")
public class IntentAuthController {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;

    public IntentAuthController(AuthenticationManager authenticationManager, TokenService tokenService, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @PostMapping("/token")
    public ResponseEntity<?> token(@Valid @RequestBody AuthTokenRequest request) {
        Authentication result;
        try {
            result = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password"));
        }
        String username = result.getName();
        TokenService.IssuedToken issued = tokenService.issue(username);
        String displayName = userService.findByUsername(username).map(User::getFullName).orElse(username);
        AuthTokenResponse response = new AuthTokenResponse(
                issued.token(),
                issued.expiresAt().atZone(WARSAW).toLocalDateTime(),
                displayName
        );
        return ResponseEntity.ok(response);
    }
}
