package com.example.hackathoncodaro2026.intent.service;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.engine.SlotRanker;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RankResult;
import com.example.hackathoncodaro2026.intent.model.RelaxStep;
import com.example.hackathoncodaro2026.intent.model.ReservationSlice;
import com.example.hackathoncodaro2026.intent.model.ResourceSlice;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;
import com.example.hackathoncodaro2026.intent.model.Suggestion;
import com.example.hackathoncodaro2026.intent.model.UserPrefs;
import com.example.hackathoncodaro2026.intent.parse.IntentParser;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.SportResourceRepository;
import com.example.hackathoncodaro2026.service.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentSchedulingService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final SportResourceRepository sportResourceRepository;
    private final ReservationRepository reservationRepository;
    private final IntentProperties intentProperties;
    private final SlotRanker slotRanker;
    private final IntentParser intentParser;
    private final PricingService pricingService;

    public IntentSchedulingService(
            SportResourceRepository sportResourceRepository,
            ReservationRepository reservationRepository,
            IntentProperties intentProperties,
            SlotRanker slotRanker,
            IntentParser intentParser,
            PricingService pricingService
    ) {
        this.sportResourceRepository = sportResourceRepository;
        this.reservationRepository = reservationRepository;
        this.intentProperties = intentProperties;
        this.slotRanker = slotRanker;
        this.intentParser = intentParser;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public SuggestOutcome suggest(String text, Integer partySize, long requestingUserId) {
        LocalDateTime now = LocalDateTime.now(WARSAW);
        int resolvedPartySize = (partySize == null || partySize < 1) ? 1 : partySize;

        IntentParser.ParseResult parsed = intentParser.parse(text, now.toLocalDate(), resolvedPartySize);
        IntentSpec spec = parsed.spec();

        LocalDate dayFrom = spec.dayFrom() == null ? now.toLocalDate() : spec.dayFrom();
        LocalDate dayTo = spec.dayTo() == null ? dayFrom : spec.dayTo();
        LocalDateTime from = dayFrom.atStartOfDay();
        LocalDateTime to = dayTo.plusDays(1).atStartOfDay();

        List<SportResource> resources = sportResourceRepository.findAllEnabledWithFacility();
        Map<Long, SportResource> resourceById = new LinkedHashMap<>();
        List<ResourceSlice> resourceSlices = new ArrayList<>(resources.size());
        for (SportResource resource : resources) {
            resourceById.put(resource.getId(), resource);
            resourceSlices.add(toSlice(resource));
        }

        List<Reservation> occupying = reservationRepository.findOccupyingOverlapping(
                ReservationStatus.occupying(),
                from,
                to
        );
        List<ReservationSlice> reservationSlices = occupying.stream().map(this::toSlice).toList();

        ScheduleSnapshot snapshot = new ScheduleSnapshot(
                resourceSlices,
                reservationSlices,
                now,
                requestingUserId,
                UserPrefs.none()
        );

        RankResult rankResult = slotRanker.rank(spec, snapshot, intentProperties);

        List<PricedSuggestion> priced = rankResult.suggestions().stream()
                .map(suggestion -> new PricedSuggestion(suggestion, priceFor(resourceById.get(suggestion.resourceId()), suggestion)))
                .toList();

        return new SuggestOutcome(spec, parsed.parserUsed(), priced, rankResult.relaxationTrail());
    }

    private String priceFor(SportResource resource, Suggestion suggestion) {
        if (resource == null) {
            return null;
        }
        long minutes = Duration.between(suggestion.start(), suggestion.end()).toMinutes();
        if (minutes <= 0 || minutes % 60 != 0) {
            return null;
        }
        int hours = (int) (minutes / 60);
        try {
            BigDecimal amount = pricingService.quote(resource, suggestion.start().toLocalDate(), suggestion.start().toLocalTime(), hours);
            if (amount == null) {
                return null;
            }
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " PLN";
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ResourceSlice toSlice(SportResource resource) {
        Map<String, String> rawAttributes = intentProperties.attributesFor(resource.getType().name());
        Map<String, Object> attributes = new LinkedHashMap<>(rawAttributes);
        String facilityName = resource.getFacility() == null ? null : resource.getFacility().getName();
        return new ResourceSlice(
                resource.getId(),
                resource.getName(),
                facilityName,
                resource.getType().name(),
                resource.getCapacity(),
                resource.getMinPartySize(),
                resource.getMaxPartySize(),
                resource.getOpeningTime(),
                resource.getClosingTime(),
                resource.getSlotDurationMinutes(),
                attributes
        );
    }

    private ReservationSlice toSlice(Reservation reservation) {
        return new ReservationSlice(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getUser().getId(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getOccupancyUnits()
        );
    }

    public record PricedSuggestion(Suggestion suggestion, String price) {
    }

    public record SuggestOutcome(
            IntentSpec spec,
            String parserUsed,
            List<PricedSuggestion> suggestions,
            List<RelaxStep> relaxationTrail
    ) {
    }
}
