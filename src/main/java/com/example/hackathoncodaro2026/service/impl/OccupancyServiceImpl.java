package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.dto.OccupancyCell;
import com.example.hackathoncodaro2026.dto.OccupancyGrid;
import com.example.hackathoncodaro2026.dto.OccupancyMapMarker;
import com.example.hackathoncodaro2026.dto.OccupancyRow;
import com.example.hackathoncodaro2026.model.Address;
import com.example.hackathoncodaro2026.model.FuneralHome;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class OccupancyServiceImpl implements OccupancyService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, double[]> DISTRICT_XY = Map.of(
            "Żoliborz", new double[]{140, 118},
            "Praga-Północ", new double[]{216, 158},
            "Wola", new double[]{96, 176},
            "Śródmieście", new double[]{148, 178},
            "Ochota", new double[]{114, 218},
            "Mokotów", new double[]{146, 258},
            "Wilanów", new double[]{176, 318}
    );
    private static final double[] CITY_CENTER = {148, 178};

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
        placeWarsawMarkers(grid, venues, occupying);
        return grid;
    }

    private void placeWarsawMarkers(OccupancyGrid grid, List<ServiceVenue> venues, List<Reservation> occupying) {
        Map<Long, OccupancyMapMarker> homes = new LinkedHashMap<>();
        for (ServiceVenue venue : venues) {
            FuneralHome home = venue.getFuneralHome();
            if (home == null || homes.containsKey(home.getId())) {
                continue;
            }
            OccupancyMapMarker marker = new OccupancyMapMarker();
            marker.setKind("home");
            marker.setName(home.getName());
            marker.setDistrict(districtOf(venue));
            marker.setDetail(home.getAddress() == null ? marker.getDistrict() : home.getAddress().toDisplayString());
            marker.setStatus("home");
            double[] xy = xyFor(marker.getDistrict(), 0);
            marker.setX(xy[0]);
            marker.setY(xy[1]);
            homes.put(home.getId(), marker);
        }
        grid.setHomes(new ArrayList<>(homes.values()));

        Set<Long> venueIds = new HashSet<>();
        for (ServiceVenue venue : venues) {
            venueIds.add(venue.getId());
        }
        Map<String, Integer> jitter = new HashMap<>();
        List<OccupancyMapMarker> people = new ArrayList<>();
        List<Reservation> remembered = occupying.stream()
                .filter(reservation -> reservation.getVenue() != null && venueIds.contains(reservation.getVenue().getId()))
                .sorted(Comparator.comparing(Reservation::getStartAt))
                .toList();
        for (Reservation reservation : remembered) {
            String district = districtOf(reservation.getVenue());
            int offset = jitter.merge(district, 1, Integer::sum);
            OccupancyMapMarker marker = new OccupancyMapMarker();
            marker.setKind("person");
            String name = reservation.getDeceasedFullName();
            marker.setName(name == null || name.isBlank() ? "Someone remembered" : name.trim());
            marker.setDistrict(district);
            marker.setDetail(
                    reservation.getVenue().getName()
                            + " · "
                            + CLOCK.format(reservation.getStartAt())
                            + " · "
                            + district
            );
            marker.setStatus(reservation.getStatus() == ReservationStatus.CONFIRMED ? "confirmed" : "pending");
            marker.setChannel(reservation.getChannelLabel());
            double[] xy = xyFor(district, offset);
            marker.setX(xy[0]);
            marker.setY(xy[1]);
            people.add(marker);
        }
        grid.setPeople(people);
    }

    private String districtOf(ServiceVenue venue) {
        Address address = venue.getAddress();
        if (address != null && address.getDistrict() != null && !address.getDistrict().isBlank()) {
            return address.getDistrict();
        }
        if (venue.getFuneralHome() != null && venue.getFuneralHome().getAddress() != null) {
            String district = venue.getFuneralHome().getAddress().getDistrict();
            if (district != null && !district.isBlank()) {
                return district;
            }
        }
        return "Śródmieście";
    }

    private double[] xyFor(String district, int offset) {
        double[] base = DISTRICT_XY.getOrDefault(district, CITY_CENTER);
        if (offset <= 0) {
            return new double[]{base[0], base[1]};
        }
        double angle = offset * 0.95;
        double radius = 7 + (offset % 3) * 3.2;
        return new double[]{
                clamp(base[0] + Math.cos(angle) * radius, 28, 292),
                clamp(base[1] + Math.sin(angle) * radius, 48, 368)
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
