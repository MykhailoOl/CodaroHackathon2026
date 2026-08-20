package com.example.hackathoncodaro2026.intent.service;

import com.example.hackathoncodaro2026.intent.engine.DefaultSlotRanker;
import com.example.hackathoncodaro2026.intent.engine.SlotRanker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntentBeansConfig {

    @Bean
    public SlotRanker slotRanker() {
        return new DefaultSlotRanker();
    }
}
