package com.example.hackathoncodaro2026.voice.sms;

public interface SmsClient {

    String send(String to, String body);
}
