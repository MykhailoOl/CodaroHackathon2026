package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.dto.OccupancyGrid;

import java.time.LocalDate;

public interface OccupancyService {

    OccupancyGrid gridFor(LocalDate date, Long funeralHomeId);
}
