package com.example.hackathoncodaro2026.intent.derive;

import com.example.hackathoncodaro2026.intent.derive.BurialWindowService.EffectiveRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BurialWindowServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 9, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final BurialWindowService service = new BurialWindowService(RiteProperties.defaults());

    private ServiceWindow derive(ArrangementFacts facts) {
        return service.derive(facts, NOW).orElseThrow();
    }

    @Test
    void withoutADateOfDeathNothingCanBeDerived() {
        assertThat(service.derive(ArrangementFacts.none(), NOW)).isEmpty();
        assertThat(service.derive(new ArrangementFacts(null, "CATHOLIC", null, 40), NOW)).isEmpty();
    }

    @Test
    void aTightRiteProducesATightWindow() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "JEWISH", TODAY, 30));
        assertThat(w.earliest()).isEqualTo(TODAY);
        assertThat(w.latest()).isEqualTo(TODAY.plusDays(1));
        assertThat(w.feasible()).isTrue();
        assertThat(w.derivation()).anyMatch(line -> line.contains("before the next sunset"));
    }

    @Test
    void theCertificateSetsTheEarliestDate() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "ORTHODOX", TODAY.plusDays(2), 40));
        assertThat(w.earliest()).isEqualTo(TODAY.plusDays(2));
        assertThat(w.latest()).isEqualTo(TODAY.plusDays(3));
        assertThat(w.derivation()).anyMatch(line -> line.contains("nothing can be scheduled before"));
    }

    @Test
    void withNoCertificateDateTheUsualLeadTimeApplies() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "ORTHODOX", null, 40));
        assertThat(w.earliest()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void aPastCertificateDateCannotPutTheWindowBehindUs() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY.minusDays(10), "HUMANIST", TODAY.minusDays(9), 20));
        assertThat(w.earliest()).isEqualTo(TODAY);
    }

    @Test
    void statuteOverridesALongerCustom() {
        // Catholic custom allows five days; the 96-hour statutory limit cuts it to four.
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "CATHOLIC", TODAY, 60));
        assertThat(w.latest()).isEqualTo(TODAY.plusDays(4));
        assertThat(w.derivation()).anyMatch(line -> line.contains("Statutory limit"));
    }

    @Test
    void aDeferrableRiteIsNotCutByStatute() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "HUMANIST", TODAY, 20));
        assertThat(w.latest()).isEqualTo(TODAY.plusDays(10));
        assertThat(w.derivation()).noneMatch(line -> line.contains("Statutory limit"));
    }

    @Test
    void anUnstatedRiteFallsBackToTheStatutoryLimit() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, null, TODAY, 40));
        assertThat(w.rite()).isNull();
        assertThat(w.latest()).isEqualTo(TODAY.plusDays(4));
        assertThat(w.derivation()).anyMatch(line -> line.contains("No observance stated"));
    }

    @Test
    void aCertificateArrivingAfterTheDeadlineIsFlaggedNotHidden() {
        // Jewish rite wants burial by tomorrow; a coroner release four days out cannot meet it.
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "JEWISH", TODAY.plusDays(4), 30));
        assertThat(w.feasible()).isFalse();
        assertThat(w.earliest()).isEqualTo(TODAY.plusDays(4));
        // Ranking still gets a usable span rather than an empty one.
        assertThat(w.latest()).isAfterOrEqualTo(w.earliest());
        assertThat(w.derivation()).anyMatch(line -> line.contains("extension of the statutory period"));
    }

    @Test
    void theDecisionDeadlineIsTheEveningBeforeTheLastDate() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "CATHOLIC", TODAY, 60));
        assertThat(w.decisionBy()).isEqualTo(TODAY.plusDays(3).atTime(21, 0));
    }

    @Test
    void theDecisionDeadlineNeverLandsInThePast() {
        // Death four days ago: the statutory limit makes today itself the last date, so
        // the evening-before cut-off has already gone. The family gets a short grace.
        ServiceWindow w = derive(new ArrangementFacts(TODAY.minusDays(4), null, TODAY, 30));
        assertThat(w.latest()).isEqualTo(TODAY);
        assertThat(w.decisionBy()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    void aWideParsedSpanIsADefaultNotAPreference() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "CATHOLIC", TODAY, 60));
        EffectiveRange range = service.applyPreference(w, TODAY, TODAY, TODAY.plusDays(7));
        assertThat(range.from()).isEqualTo(w.earliest());
        assertThat(range.to()).isEqualTo(w.latest());
        assertThat(range.note()).isNull();
    }

    @Test
    void aPreferenceInsideTheWindowNarrowsTheSearch() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "CATHOLIC", TODAY, 60));
        LocalDate wanted = TODAY.plusDays(2);
        EffectiveRange range = service.applyPreference(w, TODAY, wanted, wanted);
        assertThat(range.from()).isEqualTo(wanted);
        assertThat(range.to()).isEqualTo(wanted);
        assertThat(range.note()).contains("which the window allows");
    }

    @Test
    void theDeathDateItselfIsNotTreatedAsARequestedDay() {
        // "mum died today" makes the domain-agnostic parser emit today as the wanted day.
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "JEWISH", TODAY, 30));
        EffectiveRange range = service.applyPreference(w, TODAY, TODAY, TODAY);
        assertThat(range.from()).isEqualTo(w.earliest());
        assertThat(range.to()).isEqualTo(w.latest());
        assertThat(range.note()).isNull();
    }

    @Test
    void aPreferenceOutsideTheWindowFallsBackToTheWholeWindow() {
        ServiceWindow w = derive(new ArrangementFacts(TODAY, "JEWISH", TODAY, 30));
        LocalDate wanted = TODAY.plusDays(6);
        EffectiveRange range = service.applyPreference(w, TODAY, wanted, wanted);
        assertThat(range.from()).isEqualTo(w.earliest());
        assertThat(range.to()).isEqualTo(w.latest());
        assertThat(range.note()).contains("falls outside the window");
    }
}
