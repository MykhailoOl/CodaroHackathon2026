package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.dto.OccupancyGrid;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.OccupancyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class OccupancyController {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final OccupancyService occupancyService;
    private final CatalogService catalogService;

    public OccupancyController(OccupancyService occupancyService, CatalogService catalogService) {
        this.occupancyService = occupancyService;
        this.catalogService = catalogService;
    }

    @GetMapping({"/occupancy", "/availability"})
    public String occupancy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String homeId,
            Model model
    ) {
        LocalDate today = LocalDate.now(WARSAW);
        LocalDate selected = date == null ? today : date;
        Long homeKey = parseHomeId(homeId);
        OccupancyGrid grid = occupancyService.gridFor(selected, homeKey);
        model.addAttribute("grid", grid);
        model.addAttribute("today", today);
        model.addAttribute("selectedDate", selected);
        model.addAttribute("homeId", homeKey);
        model.addAttribute("homes", catalogService.homes());
        return "occupancy/index";
    }

    private Long parseHomeId(String homeId) {
        if (homeId == null || homeId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(homeId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
