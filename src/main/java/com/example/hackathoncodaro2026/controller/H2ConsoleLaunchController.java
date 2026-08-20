package com.example.hackathoncodaro2026.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnBooleanProperty("spring.h2.console.enabled")
public class H2ConsoleLaunchController {

    private final Environment environment;

    public H2ConsoleLaunchController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/h2-launch")
    public String launch(Model model) {
        String consolePath = environment.getProperty("spring.h2.console.path", "/h2-console");
        if (!consolePath.startsWith("/")) {
            consolePath = "/" + consolePath;
        }
        if (!consolePath.endsWith("/")) {
            consolePath = consolePath + "/";
        }
        model.addAttribute("consolePath", consolePath);
        model.addAttribute("jdbcDriver", environment.getProperty("spring.datasource.driver-class-name", "org.h2.Driver"));
        model.addAttribute("jdbcUrl", environment.getProperty("spring.datasource.url", "jdbc:h2:file:./data/everrest;LOCK_TIMEOUT=5000"));
        model.addAttribute("jdbcUser", environment.getProperty("spring.datasource.username", "sa"));
        model.addAttribute("jdbcPassword", environment.getProperty("spring.datasource.password", ""));
        return "h2-launch";
    }
}
