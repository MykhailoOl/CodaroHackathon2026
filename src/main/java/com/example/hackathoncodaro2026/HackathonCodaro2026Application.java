package com.example.hackathoncodaro2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HackathonCodaro2026Application {

    public static void main(String[] args) {
        SpringApplication.run(HackathonCodaro2026Application.class, args);
    }

}
