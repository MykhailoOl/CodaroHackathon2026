package com.example.hackathoncodaro2026.intent.engine;

import com.example.hackathoncodaro2026.intent.config.IntentProperties;
import com.example.hackathoncodaro2026.intent.model.IntentSpec;
import com.example.hackathoncodaro2026.intent.model.RankResult;
import com.example.hackathoncodaro2026.intent.model.ScheduleSnapshot;

public interface SlotRanker {

    RankResult rank(IntentSpec spec, ScheduleSnapshot snapshot, IntentProperties config);
}
