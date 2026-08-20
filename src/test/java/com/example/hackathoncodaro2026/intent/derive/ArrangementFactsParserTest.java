package com.example.hackathoncodaro2026.intent.derive;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ArrangementFactsParserTest {

    /** A Thursday, so weekday resolution is unambiguous in both directions. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    private final ArrangementFactsParser parser = new ArrangementFactsParser(RiteProperties.defaults());

    @Test
    void readsRelativeDeathDates() {
        assertThat(parser.parse("mum passed away this morning", TODAY).dateOfDeath()).isEqualTo(TODAY);
        assertThat(parser.parse("my father died last night", TODAY).dateOfDeath()).isEqualTo(TODAY.minusDays(1));
        assertThat(parser.parse("she passed away yesterday", TODAY).dateOfDeath()).isEqualTo(TODAY.minusDays(1));
        assertThat(parser.parse("he died three days ago", TODAY).dateOfDeath()).isNull();
        assertThat(parser.parse("he died 3 days ago", TODAY).dateOfDeath()).isEqualTo(TODAY.minusDays(3));
    }

    @Test
    void weekdayNamesResolveBackwardsForADeath() {
        // Tuesday before Thursday 20 Aug is 18 Aug, not the Tuesday to come.
        assertThat(parser.parse("dad died on tuesday", TODAY).dateOfDeath())
                .isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    void explicitDatesNeverResolveIntoTheFuture() {
        assertThat(parser.parse("death on 18 aug", TODAY).dateOfDeath()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(parser.parse("death on aug 18", TODAY).dateOfDeath()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(parser.parse("death on 18/08", TODAY).dateOfDeath()).isEqualTo(LocalDate.of(2026, 8, 18));
        // December is still ahead of 20 Aug 2026, so it must mean last December.
        assertThat(parser.parse("death on 2 dec", TODAY).dateOfDeath()).isEqualTo(LocalDate.of(2025, 12, 2));
    }

    @Test
    void aBookingPreferenceIsNotMistakenForTheDateOfDeath() {
        ArrangementFacts facts = parser.parse("dad died tuesday, can we hold it saturday morning", TODAY);
        assertThat(facts.dateOfDeath()).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    void aBareFutureDayWithNoDeathPhraseYieldsNoFacts() {
        assertThat(parser.parse("chapel saturday morning for 40", TODAY).schedulable()).isFalse();
    }

    @Test
    void identifiesRitesLongestPhraseFirst() {
        assertThat(parser.parse("died today, greek orthodox service", TODAY).rite()).isEqualTo("ORTHODOX");
        assertThat(parser.parse("died today, roman catholic", TODAY).rite()).isEqualTo("CATHOLIC");
        assertThat(parser.parse("died today, jewish", TODAY).rite()).isEqualTo("JEWISH");
        assertThat(parser.parse("died today, humanist ceremony", TODAY).rite()).isEqualTo("HUMANIST");
        assertThat(parser.parse("died today", TODAY).rite()).isNull();
    }

    @Test
    void readsCertificateAvailability() {
        assertThat(parser.parse("died yesterday, we have the death certificate", TODAY).certificateReadyOn())
                .isEqualTo(TODAY);
        assertThat(parser.parse("died yesterday, certificate on friday", TODAY).certificateReadyOn())
                .isEqualTo(LocalDate.of(2026, 8, 21));
        // A coroner release pushes the earliest date out by the configured lead time.
        assertThat(parser.parse("died yesterday, the coroner has the body", TODAY).certificateReadyOn())
                .isEqualTo(TODAY.minusDays(1).plusDays(4));
        assertThat(parser.parse("died yesterday", TODAY).certificateReadyOn()).isNull();
    }

    /**
     * The Telegram bot answers its own buttons by appending these exact phrases to the
     * family's message, so the backend can stay the only place that knows the rules.
     * If one of these stops parsing, the bot silently loses a fact — pin them here.
     */
    @Test
    void parsesThePhrasesTheTelegramBotEmits() {
        assertThat(parser.parse("chapel service the death was today", TODAY).dateOfDeath())
                .isEqualTo(TODAY);
        assertThat(parser.parse("chapel service the death was yesterday", TODAY).dateOfDeath())
                .isEqualTo(TODAY.minusDays(1));
        assertThat(parser.parse("chapel service the death was 2 days ago", TODAY).dateOfDeath())
                .isEqualTo(TODAY.minusDays(2));
        assertThat(parser.parse("chapel service the death was 12 august", TODAY).dateOfDeath())
                .isEqualTo(LocalDate.of(2026, 8, 12));

        String base = "chapel service the death was today ";
        assertThat(parser.parse(base + "we have the death certificate", TODAY).certificateReadyOn())
                .isEqualTo(TODAY);
        assertThat(parser.parse(base + "the certificate is not ready yet", TODAY).certificateReadyOn())
                .isNull();
        assertThat(parser.parse(base + "the coroner has the body", TODAY).certificateReadyOn())
                .isEqualTo(TODAY.plusDays(4));

        assertThat(parser.parse(base + "for 40 mourners", TODAY).mourners()).isEqualTo(40);

        for (String rite : new String[]{"catholic", "orthodox", "jewish", "muslim", "protestant", "humanist"}) {
            assertThat(parser.parse(base + rite, TODAY).rite())
                    .as("bot rite phrase '%s'", rite)
                    .isEqualTo(rite.toUpperCase(java.util.Locale.ROOT));
        }
    }

    @Test
    void countsMourners() {
        assertThat(parser.parse("died today, about 60 people", TODAY).mourners()).isEqualTo(60);
        assertThat(parser.parse("died today, 25 mourners", TODAY).mourners()).isEqualTo(25);
        assertThat(parser.parse("died today, family only", TODAY).mourners()).isEqualTo(8);
        assertThat(parser.parse("died today", TODAY).mourners()).isNull();
    }
}
