package com.example.hackathoncodaro2026.voice;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpokenLabelsTest {

    @Test
    void speaksChapelTimesWithoutHourDigits() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 21, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 21, 11, 0);
        assertThat(SpokenLabels.slot("Willow Chapel", "EverRest Warsaw", start, end))
                .isEqualTo("Willow Chapel at EverRest Warsaw, nine AM to eleven AM Friday");
    }
}
