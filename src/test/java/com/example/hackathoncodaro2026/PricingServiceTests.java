package com.example.hackathoncodaro2026;

import com.example.hackathoncodaro2026.model.SportResource;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ResourceType;
import com.example.hackathoncodaro2026.service.PricingService;
import com.example.hackathoncodaro2026.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTests {

    private final PricingService pricingService = new PricingServiceImpl();

    @Test
    void weekendEveningChapelCostsMoreThanWeekdayMorning() {
        SportResource chapel = chapel();
        BigDecimal weekdayMorning = pricingService.quote(chapel, LocalDate.of(2026, 8, 17), LocalTime.of(10, 0), 1);
        BigDecimal weekendEvening = pricingService.quote(chapel, LocalDate.of(2026, 8, 22), LocalTime.of(18, 0), 1);
        // Rates are asserted against the enum's base so a repricing of the catalogue
        // does not look like a regression in the multiplier logic under test.
        BigDecimal base = ResourceType.CHAPEL.getBaseHourlyPrice();
        assertThat(weekdayMorning).isEqualByComparingTo(base);
        assertThat(weekendEvening).isEqualByComparingTo(scale(base, "1.6875"));
        assertThat(weekendEvening).isGreaterThan(weekdayMorning);
    }

    @Test
    void twoHourQuoteSumsDaytimeAndEveningHours() {
        SportResource tennis = chapel();
        LocalDate monday = LocalDate.of(2026, 8, 17);
        BigDecimal daytime = pricingService.hourlyRate(tennis, monday, LocalTime.of(16, 0));
        BigDecimal evening = pricingService.hourlyRate(tennis, monday, LocalTime.of(17, 0));
        BigDecimal twoHours = pricingService.quote(tennis, monday, LocalTime.of(16, 0), 2);
        BigDecimal base = ResourceType.CHAPEL.getBaseHourlyPrice();
        assertThat(daytime).isEqualByComparingTo(base);
        assertThat(evening).isEqualByComparingTo(scale(base, "1.35"));
        assertThat(twoHours).isEqualByComparingTo(daytime.add(evening));
    }

    @Test
    void partySizeLabelUsesPlusOnLastGroupOption() {
        SportResource tennis = chapel();
        tennis.setMinPartySize(2);
        tennis.setMaxPartySize(4);
        assertThat(tennis.partySizeLabel(2)).isEqualTo("2");
        assertThat(tennis.partySizeLabel(3)).isEqualTo("3");
        assertThat(tennis.partySizeLabel(4)).isEqualTo("4+");
        SportResource gym = new SportResource();
        gym.setType(ResourceType.TRANSPORT);
        gym.setMinPartySize(1);
        gym.setMaxPartySize(1);
        gym.setLessonMinPartySize(2);
        gym.setLessonMaxPartySize(20);
        gym.setCapacity(20);
        assertThat(gym.partySizeLabel(1)).isEqualTo("1");
        assertThat(gym.partySizeLabel(6, ReservationKind.LESSON)).isEqualTo("6");
        assertThat(gym.partySizeLabel(20, ReservationKind.LESSON)).isEqualTo("20");
    }

    @Test
    void weekdayMorningRatesMatchEachSportBase() {
        LocalDate monday = LocalDate.of(2026, 8, 17);
        LocalTime ten = LocalTime.of(10, 0);
        for (ResourceType type : ResourceType.values()) {
            SportResource resource = resource(type);
            assertThat(pricingService.hourlyRate(resource, monday, ten))
                    .isEqualByComparingTo(type.getBaseHourlyPrice());
        }
    }

    @Test
    void eveningAndWeekendMultipliersStackWithHalfUpScale() {
        SportResource reception = resource(ResourceType.RECEPTION);
        LocalDate monday = LocalDate.of(2026, 8, 17);
        LocalDate saturday = LocalDate.of(2026, 8, 22);
        BigDecimal base = ResourceType.RECEPTION.getBaseHourlyPrice();
        assertThat(pricingService.hourlyRate(reception, monday, LocalTime.of(10, 0))).isEqualByComparingTo(base);
        assertThat(pricingService.hourlyRate(reception, monday, LocalTime.of(17, 0))).isEqualByComparingTo(scale(base, "1.35"));
        assertThat(pricingService.hourlyRate(reception, saturday, LocalTime.of(10, 0))).isEqualByComparingTo(scale(base, "1.25"));
        assertThat(pricingService.hourlyRate(reception, saturday, LocalTime.of(18, 0))).isEqualByComparingTo(scale(base, "1.6875"));
    }

    @Test
    void officiatedTransportUsesFixedLessonBaseThenMultipliers() {
        SportResource gym = resource(ResourceType.TRANSPORT);
        gym.setLessonHourlyPrice(new BigDecimal("90.00"));
        LocalDate monday = LocalDate.of(2026, 8, 17);
        LocalDate saturday = LocalDate.of(2026, 8, 22);
        assertThat(pricingService.hourlyRate(gym, monday, LocalTime.of(10, 0), ReservationKind.INDIVIDUAL))
                .isEqualByComparingTo(ResourceType.TRANSPORT.getBaseHourlyPrice());
        assertThat(pricingService.hourlyRate(gym, monday, LocalTime.of(10, 0), ReservationKind.LESSON))
                .isEqualByComparingTo("90.00");
        assertThat(pricingService.hourlyRate(gym, saturday, LocalTime.of(18, 0), ReservationKind.LESSON))
                .isEqualByComparingTo("151.88");
    }

    private SportResource chapel() {
        return resource(ResourceType.CHAPEL);
    }

    /** base x multiplier, rounded the way PricingService rounds. */
    private static BigDecimal scale(BigDecimal base, String multiplier) {
        return base.multiply(new BigDecimal(multiplier)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private SportResource resource(ResourceType type) {
        SportResource resource = new SportResource();
        resource.setType(type);
        resource.setBaseHourlyPrice(type.getBaseHourlyPrice());
        resource.setLessonHourlyPrice(type.getLessonHourlyPrice());
        resource.setMinPartySize(type.getMinPartySize());
        resource.setMaxPartySize(type.getMaxPartySize());
        return resource;
    }
}
