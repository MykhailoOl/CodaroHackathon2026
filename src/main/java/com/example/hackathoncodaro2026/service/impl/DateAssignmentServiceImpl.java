package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.config.SchedulingProperties;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.service.DateAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.random.RandomGenerator;

@Service
@Transactional(readOnly = true)
public class DateAssignmentServiceImpl implements DateAssignmentService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ReservationRepository reservationRepository;
    private final SchedulingProperties schedulingProperties;
    private final RandomGenerator randomGenerator;

    public DateAssignmentServiceImpl(
            ReservationRepository reservationRepository,
            SchedulingProperties schedulingProperties,
            RandomGenerator randomGenerator
    ) {
        this.reservationRepository = reservationRepository;
        this.schedulingProperties = schedulingProperties;
        this.randomGenerator = randomGenerator;
    }

    @Override
    public List<LocalDateTime> availableStarts(ServiceVenue venue, FuneralPackage funeralPackage) {
        if (venue == null || funeralPackage == null) {
            return List.of();
        }
        int duration = funeralPackage.getDurationMinutes();
        int step = Math.max(15, venue.getSlotDurationMinutes());
        LocalDateTime now = LocalDateTime.now(WARSAW);
        LocalDate today = now.toLocalDate();
        int days = Math.max(1, schedulingProperties.getPlanningDays());
        List<LocalDateTime> starts = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            LocalDate date = today.plusDays(d);
            if (!isOpenDay(date)) {
                continue;
            }
            LocalTime cursor = venue.getOpeningTime();
            LocalTime close = venue.getClosingTime();
            while (!cursor.plusMinutes(duration).isAfter(close)) {
                LocalDateTime startAt = LocalDateTime.of(date, cursor);
                LocalDateTime endAt = startAt.plusMinutes(duration);
                if (startAt.isAfter(now)
                        && reservationRepository.countOverlapping(
                        venue.getId(),
                        ReservationStatus.occupying(),
                        startAt,
                        endAt
                ) == 0) {
                    starts.add(startAt);
                }
                cursor = cursor.plusMinutes(step);
                if (cursor.getHour() >= 24) {
                    break;
                }
            }
        }
        return starts;
    }

    @Override
    public List<LocalDate> previewDates(ServiceVenue venue, FuneralPackage funeralPackage) {
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
        for (LocalDateTime start : availableStarts(venue, funeralPackage)) {
            dates.add(start.toLocalDate());
            if (dates.size() >= schedulingProperties.getCandidateDates()) {
                break;
            }
        }
        return List.copyOf(dates);
    }

    @Override
    public LocalDateTime chooseStart(ServiceVenue venue, FuneralPackage funeralPackage) {
        List<LocalDateTime> starts = availableStarts(venue, funeralPackage);
        if (starts.isEmpty()) {
            return null;
        }
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
        for (LocalDateTime start : starts) {
            dates.add(start.toLocalDate());
        }
        List<LocalDate> dateList = List.copyOf(dates);
        LocalDate chosenDate = dateList.get(randomGenerator.nextInt(dateList.size()));
        List<LocalDateTime> onDate = new ArrayList<>();
        for (LocalDateTime start : starts) {
            if (start.toLocalDate().equals(chosenDate)) {
                onDate.add(start);
            }
        }
        return onDate.get(randomGenerator.nextInt(onDate.size()));
    }

    private boolean isOpenDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY && !schedulingProperties.isSundayEnabled()) {
            return false;
        }
        return true;
    }
}
