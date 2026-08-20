package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.voice.dto.CheckAvailabilityRequest;
import com.example.hackathoncodaro2026.voice.dto.CheckAvailabilityResponse;
import com.example.hackathoncodaro2026.voice.dto.CreateBookingRequest;
import com.example.hackathoncodaro2026.voice.dto.CreateBookingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice")
public class VoiceToolController {

    private final VoiceBookingService voiceBookingService;
    private final VoiceToolAuthenticator authenticator;

    private final ElevenLabsAgentProvisioner elevenLabsAgentProvisioner;

    public VoiceToolController(
            VoiceBookingService voiceBookingService,
            VoiceToolAuthenticator authenticator,
            ElevenLabsAgentProvisioner elevenLabsAgentProvisioner
    ) {
        this.voiceBookingService = voiceBookingService;
        this.authenticator = authenticator;
        this.elevenLabsAgentProvisioner = elevenLabsAgentProvisioner;
    }

    @GetMapping(value = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public VoiceBookingService.VoiceCatalog catalog() {
        return voiceBookingService.catalog();
    }

    @PostMapping(value = "/tools/check-availability", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckAvailabilityResponse checkAvailability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Tool-Secret", required = false) String toolSecret,
            @RequestBody CheckAvailabilityRequest request
    ) {
        authenticator.requireSecret(authorization, toolSecret);
        return voiceBookingService.checkAvailability(request);
    }

    @PostMapping(value = "/tools/create-booking", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateBookingResponse createBooking(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Tool-Secret", required = false) String toolSecret,
            @RequestBody CreateBookingRequest request
    ) {
        authenticator.requireSecret(authorization, toolSecret);
        return voiceBookingService.createBooking(request);
    }

    @PostMapping(value = "/provision", produces = MediaType.APPLICATION_JSON_VALUE)
    public java.util.Map<String, Object> provision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Tool-Secret", required = false) String toolSecret
    ) {
        authenticator.requireSecret(authorization, toolSecret);
        return elevenLabsAgentProvisioner.provision();
    }
}
