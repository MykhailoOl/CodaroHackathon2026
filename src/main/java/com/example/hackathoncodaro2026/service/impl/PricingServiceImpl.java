package com.example.hackathoncodaro2026.service.impl;

import com.example.hackathoncodaro2026.model.ArrangementExtra;
import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PricingMode;
import com.example.hackathoncodaro2026.service.PricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

@Service
public class PricingServiceImpl implements PricingService {

    @Override
    public BigDecimal quote(FuneralPackage funeralPackage, Collection<ArrangementExtra> extras, int attendees) {
        BigDecimal total = funeralPackage == null
                ? BigDecimal.ZERO
                : funeralPackage.getBasePrice();
        int heads = Math.max(1, attendees);
        if (extras != null) {
            for (ArrangementExtra extra : extras) {
                if (extra == null || extra.getAmount() == null) {
                    continue;
                }
                BigDecimal line = extra.getAmount();
                if (extra.getPricingMode() == PricingMode.PER_ATTENDEE) {
                    line = line.multiply(BigDecimal.valueOf(heads));
                }
                total = total.add(line);
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
