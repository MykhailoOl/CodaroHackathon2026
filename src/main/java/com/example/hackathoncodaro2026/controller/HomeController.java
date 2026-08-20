package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.model.User;
import com.example.hackathoncodaro2026.service.CatalogService;
import com.example.hackathoncodaro2026.service.OccupancyService;
import com.example.hackathoncodaro2026.service.ReservationService;
import com.example.hackathoncodaro2026.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class HomeController {

    private final UserService userService;
    private final CatalogService catalogService;
    private final ReservationService reservationService;
    private final OccupancyService occupancyService;

    public HomeController(
            UserService userService,
            CatalogService catalogService,
            ReservationService reservationService,
            OccupancyService occupancyService
    ) {
        this.userService = userService;
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        this.occupancyService = occupancyService;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("homes", catalogService.homes());
        model.addAttribute("homeCount", catalogService.homes().size());
        model.addAttribute(
                "occupancyPercent",
                occupancyService.gridFor(LocalDate.now(ZoneId.of("Europe/Warsaw")), null).getFillPercent()
        );
        long upcoming = 0;
        if (authentication != null) {
            User user = userService.findByUsername(authentication.getName()).orElse(null);
            if (user != null) {
                upcoming = reservationService.countUpcomingActive(user);
            }
        }
        model.addAttribute("upcomingCount", upcoming);
        return "home/index";
    }
}
