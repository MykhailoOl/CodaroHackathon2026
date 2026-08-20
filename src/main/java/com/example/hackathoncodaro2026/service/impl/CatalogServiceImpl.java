package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.FuneralHome;
import com.example.hackathoncodaro2026.model.ServiceVenue;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.repository.ArrangementExtraRepository;
import com.example.hackathoncodaro2026.repository.FuneralHomeRepository;
import com.example.hackathoncodaro2026.repository.ServiceVenueRepository;
import com.example.hackathoncodaro2026.service.CatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

    private final FuneralHomeRepository funeralHomeRepository;
    private final ServiceVenueRepository serviceVenueRepository;
    private final ArrangementExtraRepository arrangementExtraRepository;

    public CatalogServiceImpl(
            FuneralHomeRepository funeralHomeRepository,
            ServiceVenueRepository serviceVenueRepository,
            ArrangementExtraRepository arrangementExtraRepository
    ) {
        this.funeralHomeRepository = funeralHomeRepository;
        this.serviceVenueRepository = serviceVenueRepository;
        this.arrangementExtraRepository = arrangementExtraRepository;
    }

    @Override
    public List<FuneralHome> homes() {
        return funeralHomeRepository.findByEnabledTrueOrderByNameAsc();
    }

    @Override
    public Optional<FuneralHome> home(Long id) {
        return funeralHomeRepository.findByIdAndEnabledTrue(id);
    }

    @Override
    public List<ServiceVenue> venues(Long homeId) {
        return serviceVenueRepository.findByFuneralHome_IdAndEnabledTrueOrderByNameAsc(homeId);
    }

    @Override
    public Optional<ServiceVenue> venue(Long id) {
        return serviceVenueRepository.findEnabledWithHome(id);
    }

    @Override
    public List<ArrangementExtra> extras() {
        return arrangementExtraRepository.findByEnabledTrueOrderByNameAsc();
    }

    @Override
    public List<ArrangementExtra> extras(ServiceType serviceType) {
        List<ArrangementExtra> result = new ArrayList<>();
        for (ArrangementExtra extra : arrangementExtraRepository.findByEnabledTrueOrderByNameAsc()) {
            if (extra.appliesTo(serviceType)) {
                result.add(extra);
            }
        }
        return result;
    }
}
