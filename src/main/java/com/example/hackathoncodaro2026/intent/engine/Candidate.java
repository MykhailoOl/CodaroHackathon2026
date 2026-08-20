package com.example.hackathoncodaro2026.intent.engine;

import java.time.LocalDateTime;

record Candidate(long resourceId, LocalDateTime start, LocalDateTime end, Interval sourceInterval) {
}
