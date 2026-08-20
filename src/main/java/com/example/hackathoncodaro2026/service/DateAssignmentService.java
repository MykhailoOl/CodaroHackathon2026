package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DateAssignmentService {

    List<LocalDateTime> availableStarts(ServiceVenue venue, FuneralPackage funeralPackage);

    List<LocalDate> previewDates(ServiceVenue venue, FuneralPackage funeralPackage);

    LocalDateTime chooseStart(ServiceVenue venue, FuneralPackage funeralPackage);
}
