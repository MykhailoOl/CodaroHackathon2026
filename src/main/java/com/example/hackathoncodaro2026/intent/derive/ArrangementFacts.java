package com.example.hackathoncodaro2026.intent.derive;

import java.time.LocalDate;

/**
 * The facts a family states about the deceased, as opposed to the preferences a
 * customer states about themselves.
 *
 * <p>This is the structural inversion the product is built on: in a normal booking
 * the person who books is the person who attends and chooses the date. Here the
 * subject of the booking is deceased and cannot choose, the payer is a third party
 * under time pressure, and the date is <em>derived</em> from these facts rather than
 * selected from a grid. Everything downstream reads from this record.
 *
 * @param dateOfDeath       when the death occurred; null when the family has not said
 * @param rite              observance key (see {@link RiteProperties}); null when unstated
 * @param certificateReadyOn earliest date the death certificate / coroner release is
 *                          expected; null means "use the configured lead time"
 * @param mourners          expected attendance; null when unstated
 */
public record ArrangementFacts(
        LocalDate dateOfDeath,
        String rite,
        LocalDate certificateReadyOn,
        Integer mourners
) {

    public static ArrangementFacts none() {
        return new ArrangementFacts(null, null, null, null);
    }

    /** True once we know enough to derive a window; without a date of death we cannot. */
    public boolean schedulable() {
        return dateOfDeath != null;
    }
}
