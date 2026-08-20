package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.enums.ServiceType;

import java.util.List;
import java.util.Optional;

public interface CatalogService {

    List<FuneralHome> homes();

    Optional<FuneralHome> home(Long id);

    List<ServiceVenue> venues(Long homeId);

    List<ServiceVenue> venues();

    Optional<ServiceVenue> venue(Long id);

    List<ArrangementExtra> extras();

    List<ArrangementExtra> extras(ServiceType serviceType);
}
