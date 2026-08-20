package com.example.hackathoncodaro2026.controller;

import com.example.hackathoncodaro2026.exception.ReservationException;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FuneralHomeController {

    private final CatalogService catalogService;

    public FuneralHomeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/homes")
    public String list(Model model) {
        model.addAttribute("homes", catalogService.homes());
        return "homes/list";
    }

    @GetMapping("/homes/{id}")
    public String detail(@PathVariable Long id, Model model) {
        FuneralHome home = catalogService.home(id)
                .orElseThrow(() -> new ReservationException("That funeral home could not be found"));
        model.addAttribute("home", home);
        model.addAttribute("venues", catalogService.venues(id));
        return "homes/detail";
    }
}
