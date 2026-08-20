package com.example.hackathoncodaro2026.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class AssignmentConfig {

    @Bean
    public RandomGenerator assignmentRandom() {
        return new SecureRandom();
    }
}
