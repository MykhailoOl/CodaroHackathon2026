package com.example.hackathoncodaro2026.intent.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntervalsTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 20, hour, minute);
    }

    @Test
    void subtractsSeparatedReservationsIntoThreeFreeGaps() {
        Interval window = new Interval(at(8, 0), at(12, 0));
        List<Interval> busy = List.of(
                new Interval(at(9, 0), at(10, 0)),
                new Interval(at(11, 0), at(11, 30)));

        List<Interval> free = Intervals.subtract(window, busy);

        assertEquals(List.of(
                new Interval(at(8, 0), at(9, 0)),
                new Interval(at(10, 0), at(11, 0)),
                new Interval(at(11, 30), at(12, 0))
        ), free);
    }

    @Test
    void exactlyTouchingReservationsMergeWithNoGapBetween() {
        Interval window = new Interval(at(8, 0), at(12, 0));
        List<Interval> busy = List.of(
                new Interval(at(9, 0), at(10, 0)),
                new Interval(at(10, 0), at(11, 0)));

        List<Interval> free = Intervals.subtract(window, busy);

        assertEquals(List.of(
                new Interval(at(8, 0), at(9, 0)),
                new Interval(at(11, 0), at(12, 0))
        ), free);
    }

    @Test
    void zeroLengthReservationIsIgnored() {
        Interval window = new Interval(at(8, 0), at(12, 0));
        List<Interval> busy = List.of(new Interval(at(9, 0), at(9, 0)));

        List<Interval> free = Intervals.subtract(window, busy);

        assertEquals(List.of(new Interval(at(8, 0), at(12, 0))), free);
    }

    @Test
    void reservationFullyCoveringWindowLeavesNoFreeTime() {
        Interval window = new Interval(at(8, 0), at(12, 0));
        List<Interval> busy = List.of(new Interval(at(7, 0), at(13, 0)));

        List<Interval> free = Intervals.subtract(window, busy);

        assertEquals(List.of(), free);
    }

    @Test
    void reservationOutsideWindowDoesNotAffectFreeTime() {
        Interval window = new Interval(at(8, 0), at(12, 0));
        List<Interval> busy = List.of(new Interval(at(13, 0), at(14, 0)));

        List<Interval> free = Intervals.subtract(window, busy);

        assertEquals(List.of(new Interval(at(8, 0), at(12, 0))), free);
    }
}
