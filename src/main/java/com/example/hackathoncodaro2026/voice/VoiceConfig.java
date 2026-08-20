package com.example.hackathoncodaro2026.voice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VoiceProperties.class)
public class VoiceConfig {

    @Bean
    public ElevenLabsRemote elevenLabsRemote(VoiceProperties properties) {
        return new ElevenLabsHttpRemote(properties);
    }
}
