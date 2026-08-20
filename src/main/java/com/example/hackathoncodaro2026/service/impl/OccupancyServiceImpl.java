package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.dto.OccupancyCell;
import com.example.hackathoncodaro2026.dto.OccupancyGrid;
import com.example.hackathoncodaro2026.dto.OccupancyRow;
import com.example.hackathoncodaro2026.model.Reservation;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.repository.ReservationRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.service.OccupancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OccupancyServiceImpl implements OccupancyService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ServiceVenueRepository serviceVenueRepository;
    private final ReservationRepository reservationRepository;

    public OccupancyServiceImpl(
            ServiceVenueRepository serviceVenueRepository,
            ReservationRepository reservationRepository
    ) {
        this.serviceVenueRepository = serviceVenueRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public OccupancyGrid gridFor(LocalDate date, Long funeralHomeId) {
        LocalDate day = date == null ? LocalDate.now(WARSAW) : date;
        OccupancyGrid grid = new OccupancyGrid();
        grid.setDate(day);
        grid.setFuneralHomeId(funeralHomeId);
        List<ServiceVenue> venues = funeralHomeId == null
                ? serviceVenueRepository.findByEnabledTrueOrderByNameAsc()
                : serviceVenueRepository.findByFuneralHome_IdAndEnabledTrueOrderByNameAsc(funeralHomeId);
        LocalTime open = LocalTime.of(8, 0);
        LocalTime close = LocalTime.of(18, 0);
        for (ServiceVenue venue : venues) {
            if (venue.getOpeningTime() != null && venue.getOpeningTime().isBefore(open)) {
                open = venue.getOpeningTime();
            }
            if (venue.getClosingTime() != null && venue.getClosingTime().isAfter(close)) {
                close = venue.getClosingTime();
            }
        }
        List<LocalTime> hours = new ArrayList<>();
        LocalTime cursor = open;
        while (cursor.isBefore(close)) {
            hours.add(cursor);
            cursor = cursor.plusHours(1);
        }
        grid.setHours(hours);
        int bookedUnits = 0;
        int capacityUnits = 0;
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        List<Reservation> occupying = reservationRepository.findOccupyingOverlapping(
                ReservationStatus.occupying(),
                dayStart,
                dayEnd
        );
        for (ServiceVenue venue : venues) {
            OccupancyRow row = new OccupancyRow();
            row.setVenueId(venue.getId());
            row.setVenueName(venue.getName());
            row.setFuneralHomeName(venue.getFuneralHome() == null ? "" : venue.getFuneralHome().getName());
            row.setVenueType(venue.getType() == null ? "" : venue.getType().getLabel());
            row.setImagePath(venue.resolvedImagePath());
            row.setMaxAttendees(venue.getMaxAttendees());
            for (LocalTime hour : hours) {
                LocalDateTime slotStart = LocalDateTime.of(day, hour);
                LocalDateTime slotEnd = slotStart.plusHours(1);
                int booked = 0;
                String level = "free";
                for (Reservation reservation : occupying) {
                    if (!reservation.getVenue().getId().equals(venue.getId())) {
                        continue;
                    }
                    if (reservation.getStartAt().isBefore(slotEnd) && reservation.getEndAt().isAfter(slotStart)) {
                        booked = 1;
                        level = reservation.getStatus() == ReservationStatus.CONFIRMED ? "confirmed" : "pending";
                        break;
                    }
                }
                row.getCells().add(new OccupancyCell(hour, booked, 1, level, false));
                bookedUnits += booked;
                capacityUnits += 1;
            }
            grid.getRows().add(row);
        }
        grid.setBookedUnits(bookedUnits);
        grid.setCapacityUnits(capacityUnits);
        return grid;
    }
}
