package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.ArrangementFieldMapper;
import com.example.hackathoncodaro2026.dto.assistant.AssistantErrorResponse;
import com.example.hackathoncodaro2026.exception.AssistantException;
import com.example.hackathoncodaro2026.exception.ReservationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {ReservationAssistantController.class, VenueController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationAssistantExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationAssistantExceptionHandler.class);

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<AssistantErrorResponse> handleAssistant(AssistantException exception) {
        log.warn(
                "Assistant rejected code={} requestId={}",
                exception.getCode(),
                requestId()
        );
        return ResponseEntity.status(exception.getStatus())
                .body(new AssistantErrorResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getField(),
                        exception.getStep()
                ));
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<AssistantErrorResponse> handleReservation(ReservationException exception) {
        log.warn("Assistant reservation rejected requestId={}", requestId());
        String code = exception.getCode() == null || exception.getCode().isBlank() ? "VALIDATION" : exception.getCode();
        String field = exception.getField();
        HttpStatus status = "NO_SLOTS".equals(code) || "STALE_SLOT".equals(code) || "LOCK_TIMEOUT".equals(code)
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new AssistantErrorResponse(code, exception.getMessage(), field, ArrangementFieldMapper.stepFor(field)));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<AssistantErrorResponse> handleBadRequest(Exception exception) {
        log.warn("Assistant bad request requestId={}", requestId());
        String message = "Please check the booking details.";
        String field = null;
        if (exception instanceof MethodArgumentNotValidException validation) {
            FieldError fieldError = validation.getBindingResult().getFieldError();
            if (fieldError != null) {
                field = fieldError.getField();
                if (fieldError.getDefaultMessage() != null) {
                    message = fieldError.getDefaultMessage();
                }
            }
        }
        return ResponseEntity.badRequest()
                .body(new AssistantErrorResponse("VALIDATION", message, field, ArrangementFieldMapper.stepFor(field)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AssistantErrorResponse> handleDenied() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AssistantErrorResponse("UNAUTHENTICATED", "Sign in to reserve."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AssistantErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled assistant exception requestId={}", requestId(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AssistantErrorResponse("ERROR", "Something went wrong. Please try again."));
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? "" : value;
    }
}
