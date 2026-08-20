package com.example.hackathoncodaro2026.intent.parse;

import com.example.hackathoncodaro2026.intent.model.IntentSpec;

import java.time.LocalDate;

public interface IntentParser {

    ParseResult parse(String text, LocalDate today, int partySize);

    record ParseResult(IntentSpec spec, String parserUsed) {
    }
}
