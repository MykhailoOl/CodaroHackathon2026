package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.assistant.AssistantCreateResponse;
import com.example.hackathoncodaro2026.dto.assistant.AssistantExtraOptionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantHomeDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantPreviewDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantQuoteDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantSessionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueCardDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueDetailDto;
import com.example.hackathoncodaro2026.exception.AssistantException;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.service.ReservationAssistantService;
import com.example.hackathoncodaro2026.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/reservation-assistant", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReservationAssistantController {

    private final ReservationAssistantService reservationAssistantService;
    private final UserService userService;

    public ReservationAssistantController(
            ReservationAssistantService reservationAssistantService,
            UserService userService
    ) {
        this.reservationAssistantService = reservationAssistantService;
        this.userService = userService;
    }

    @GetMapping("/session")
    public AssistantSessionDto session(Authentication authentication) {
        return reservationAssistantService.session(requireUser(authentication));
    }

    @GetMapping("/homes")
    public List<AssistantHomeDto> homes(Authentication authentication) {
        requireUser(authentication);
        return reservationAssistantService.homes();
    }

    @GetMapping("/homes/{id}/venues")
    public List<AssistantVenueCardDto> venues(@PathVariable Long id, Authentication authentication) {
        requireUser(authentication);
        return reservationAssistantService.venues(id);
    }

    @GetMapping("/venues/{id}")
    public AssistantVenueDetailDto venue(@PathVariable Long id, Authentication authentication) {
        requireUser(authentication);
        return reservationAssistantService.venue(id);
    }

    @GetMapping("/venues/{id}/extras")
    public List<AssistantExtraOptionDto> extras(
            @PathVariable Long id,
            @RequestParam ServiceType serviceType,
            Authentication authentication
    ) {
        requireUser(authentication);
        return reservationAssistantService.extras(id, serviceType);
    }

    @PostMapping(path = "/quote", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssistantQuoteDto quote(
            @Valid @RequestBody ArrangementRequest request,
            Authentication authentication
    ) {
        return reservationAssistantService.quote(requireUser(authentication), request);
    }

    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssistantPreviewDto preview(
            @Valid @RequestBody ArrangementRequest request,
            Authentication authentication
    ) {
        return reservationAssistantService.preview(requireUser(authentication), request);
    }

    @PostMapping(path = "/arrangements", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssistantCreateResponse spin(
            @Valid @RequestBody ArrangementRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        request.setBookingSource("CHAT_ASSISTANT");
        return reservationAssistantService.spin(user, request);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AssistantException("UNAUTHENTICATED", "Sign in to arrange a ceremony.", HttpStatus.UNAUTHORIZED);
        }
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ReservationException("Signed-in user was not found"));
    }
}
