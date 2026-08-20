package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.dto.ArrangementCreateResponse;
import com.example.hackathoncodaro2026.dto.ArrangementFieldMapper;
import com.example.hackathoncodaro2026.dto.ArrangementPreview;
import com.example.hackathoncodaro2026.dto.ArrangementRequest;
import com.example.hackathoncodaro2026.dto.PriceQuote;
import com.example.hackathoncodaro2026.dto.assistant.AssistantCreateResponse;
import com.example.hackathoncodaro2026.dto.assistant.AssistantExtraOptionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantHomeDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantPackageDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantPreviewDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantQuoteDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantServiceOptionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantSessionDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueCardDto;
import com.example.hackathoncodaro2026.dto.assistant.AssistantVenueDetailDto;
import com.example.hackathoncodaro2026.exception.AssistantException;
import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.Role;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.service.AuditLogService;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.ReservationAssistantService;
import com.example.hackathoncodaro2026.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReservationAssistantServiceImpl implements ReservationAssistantService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final CatalogService catalogService;
    private final ReservationService reservationService;
    private final AuditLogService auditLogService;

    public ReservationAssistantServiceImpl(
            CatalogService catalogService,
            ReservationService reservationService,
            AuditLogService auditLogService
    ) {
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        this.auditLogService = auditLogService;
    }

    @Override
    public AssistantSessionDto session(User user) {
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER;
        boolean phone = user.getPhone() == null || user.getPhone().isBlank();
        return new AssistantSessionDto(
                true,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                phone,
                staff ? ReservationStatus.CONFIRMED.name() : ReservationStatus.PENDING.name()
        );
    }

    @Override
    public List<AssistantHomeDto> homes() {
        List<AssistantHomeDto> result = new ArrayList<>();
        for (FuneralHome home : catalogService.homes()) {
            String address = home.getAddress() == null ? "" : home.getAddress().toDisplayString();
            result.add(new AssistantHomeDto(home.getId(), home.getName(), address, home.getImagePath()));
        }
        return result;
    }

    @Override
    public List<AssistantVenueCardDto> venues(Long homeId) {
        catalogService.home(homeId)
                .orElseThrow(() -> new AssistantException("VALIDATION", "That funeral home could not be found", HttpStatus.NOT_FOUND));
        List<AssistantVenueCardDto> result = new ArrayList<>();
        for (ServiceVenue venue : catalogService.venues(homeId)) {
            result.add(toCard(venue));
        }
        return result;
    }

    @Override
    public AssistantVenueDetailDto venue(Long venueId) {
        ServiceVenue venue = catalogService.venue(venueId)
                .orElseThrow(() -> new AssistantException("VALIDATION", "That venue could not be found", HttpStatus.NOT_FOUND));
        List<AssistantServiceOptionDto> services = new ArrayList<>();
        for (ServiceType type : ServiceType.values()) {
            if (type.allows(venue.getType())) {
                services.add(new AssistantServiceOptionDto(type.name(), type.getLabel()));
            }
        }
        List<AssistantPackageDto> packages = new ArrayList<>();
        for (FuneralPackage funeralPackage : FuneralPackage.values()) {
            packages.add(new AssistantPackageDto(
                    funeralPackage.name(),
                    funeralPackage.getLabel(),
                    funeralPackage.getDescription(),
                    funeralPackage.getDurationMinutes(),
                    funeralPackage.getBasePrice()
            ));
        }
        return new AssistantVenueDetailDto(
                venue.getId(),
                venue.getFuneralHome().getId(),
                venue.getFuneralHome().getName(),
                venue.getName(),
                venue.getType().name(),
                venue.getType().getLabel(),
                venue.getAddress() == null ? "" : venue.getAddress().toDisplayString(),
                venue.getOpeningTime().format(HM),
                venue.getClosingTime().format(HM),
                venue.getMaxAttendees(),
                venue.resolvedImagePath(),
                services,
                packages
        );
    }

    @Override
    public List<AssistantExtraOptionDto> extras(Long venueId, ServiceType serviceType) {
        catalogService.venue(venueId)
                .orElseThrow(() -> new AssistantException("VALIDATION", "That venue could not be found", HttpStatus.NOT_FOUND));
        List<AssistantExtraOptionDto> result = new ArrayList<>();
        for (ArrangementExtra extra : catalogService.extras(serviceType)) {
            result.add(new AssistantExtraOptionDto(
                    extra.getId(),
                    extra.getName(),
                    extra.getAmount(),
                    extra.getPricingMode().name()
            ));
        }
        return result;
    }

    @Override
    public AssistantQuoteDto quote(User user, ArrangementRequest request) {
        try {
            PriceQuote quote = reservationService.quote(user, request);
            boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER;
            return new AssistantQuoteDto(
                    quote.getAmount(),
                    quote.getCurrency(),
                    staff ? ReservationStatus.CONFIRMED.name() : ReservationStatus.PENDING.name()
            );
        } catch (ReservationException ex) {
            throw map(user, ex);
        }
    }

    @Override
    public AssistantPreviewDto preview(User user, ArrangementRequest request) {
        try {
            ArrangementPreview preview = reservationService.preview(user, request);
            return new AssistantPreviewDto(
                    preview.dates(),
                    preview.amount(),
                    preview.currency(),
                    preview.expectedStatus(),
                    preview.notice()
            );
        } catch (ReservationException ex) {
            throw map(user, ex);
        }
    }

    @Override
    @Transactional
    public AssistantCreateResponse spin(User user, ArrangementRequest request) {
        request.setBookingSource("CHAT_ASSISTANT");
        try {
            ArrangementCreateResponse created = reservationService.spin(user, request);
            return new AssistantCreateResponse(
                    created.id(),
                    created.status(),
                    created.amount(),
                    created.formattedAmount(),
                    created.startAt(),
                    created.endAt(),
                    created.dates()
            );
        } catch (ReservationException ex) {
            throw map(user, ex);
        }
    }

    private AssistantVenueCardDto toCard(ServiceVenue venue) {
        return new AssistantVenueCardDto(
                venue.getId(),
                venue.getName(),
                venue.getType().name(),
                venue.getType().getLabel(),
                venue.getAddress() == null ? "" : venue.getAddress().toDisplayString(),
                venue.getOpeningTime().format(HM),
                venue.getClosingTime().format(HM),
                venue.getMaxAttendees(),
                venue.resolvedImagePath()
        );
    }

    private AssistantException map(User user, ReservationException ex) {
        String message = ex.getMessage() == null ? "That arrangement could not be completed." : ex.getMessage();
        String code = ex.getCode();
        if (code == null || code.isBlank()) {
            String lower = message.toLowerCase();
            code = "VALIDATION";
            if (lower.contains("just assigned") || lower.contains("spin again")) {
                code = "STALE_SLOT";
            } else if (lower.contains("no ceremony times")) {
                code = "NO_SLOTS";
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", code);
        details.put("source", "CHAT_ASSISTANT");
        auditLogService.record(user, "ASSISTANT_RESERVATION_REJECTED", "RESERVATION", null, "REJECTED", details);
        HttpStatus status = "NO_SLOTS".equals(code) || "STALE_SLOT".equals(code) || "LOCK_TIMEOUT".equals(code)
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return new AssistantException(code, message, status, ex.getField(), ArrangementFieldMapper.stepFor(ex.getField()));
    }
}
