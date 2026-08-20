package com.example.hackathoncodaro2026.voice.sms;

public interface SmsClient {

    void send(String to, String body);
}
