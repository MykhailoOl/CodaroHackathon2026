package com.example.hackathoncodaro2026.service;

import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;

import java.math.BigDecimal;
import java.util.Collection;

public interface PricingService {

    BigDecimal quote(FuneralPackage funeralPackage, Collection<ArrangementExtra> extras, int attendees);
}
