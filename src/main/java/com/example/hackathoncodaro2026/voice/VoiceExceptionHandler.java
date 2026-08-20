package com.example.hackathoncodaro2026.voice;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.voice.dto.VoiceToolError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = VoiceToolController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VoiceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceExceptionHandler.class);

    @ExceptionHandler(VoiceToolException.class)
    public ResponseEntity<VoiceToolError> handleVoice(VoiceToolException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new VoiceToolError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<VoiceToolError> handleReservation(ReservationException exception) {
        return ResponseEntity.status(409)
                .body(new VoiceToolError("slot_no_longer_available", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<VoiceToolError> handleUnexpected(Exception exception) {
        log.error("Voice tool failed", exception);
        return ResponseEntity.status(500)
                .body(new VoiceToolError("internal", "I could not complete that booking just now."));
    }
}
