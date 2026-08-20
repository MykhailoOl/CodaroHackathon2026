package com.example.hackathoncodaro2026.voice.sms;

import com.example.hackathoncodaro2026.voice.VoiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LoggingSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsClient.class);

    private final CopyOnWriteArrayList<SmsMessage> sent = new CopyOnWriteArrayList<>();
    private final VoiceProperties properties;

    public LoggingSmsClient(VoiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String to, String body) {
        sent.add(new SmsMessage(to, body));
        log.info(
                "SMS placeholder provider={} from={} to={} bodyChars={}",
                properties.getSms().getProvider(),
                properties.getSms().getFrom(),
                mask(to),
                body == null ? 0 : body.length()
        );
    }

    public List<SmsMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    private String mask(String to) {
        if (to == null || to.length() < 4) {
            return "***";
        }
        String digits = to.replaceAll("\\D", "");
        if (digits.length() < 3) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 3);
    }
}
