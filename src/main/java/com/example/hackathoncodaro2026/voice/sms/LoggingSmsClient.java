package com.example.hackathoncodaro2026.voice.sms;

import com.example.hackathoncodaro2026.voice.VoiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LoggingSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsClient.class);

    private final CopyOnWriteArrayList<SmsMessage> sent = new CopyOnWriteArrayList<>();
    private final VoiceProperties properties;
    private final RestClient restClient = RestClient.create();

    public LoggingSmsClient(VoiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String send(String to, String body) {
        sent.add(new SmsMessage(to, body));
        if (!telnyxReady()) {
            log.info(
                    "SMS placeholder provider={} from={} to={} bodyChars={}",
                    properties.getSms().getProvider(),
                    properties.getSms().getFrom(),
                    mask(to),
                    body == null ? 0 : body.length()
            );
            return "logged";
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messaging_profile_id", properties.getSms().getTelnyxMessagingProfileId().trim());
            payload.put("from", properties.getSms().getFrom());
            payload.put("to", to);
            payload.put("text", body);
            restClient.post()
                    .uri("https://api.telnyx.com/v2/messages")
                    .header("Authorization", "Bearer " + properties.getSms().getTelnyxApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("SMS telnyx from={} to={} bodyChars={}", properties.getSms().getFrom(), mask(to), body == null ? 0 : body.length());
            return "sent";
        } catch (RestClientResponseException ex) {
            log.warn("SMS telnyx failed status={} to={}", ex.getStatusCode().value(), mask(to));
            return "failed";
        } catch (RuntimeException ex) {
            log.warn("SMS telnyx failed to={} cause={}", mask(to), ex.getClass().getSimpleName());
            return "failed";
        }
    }

    public List<SmsMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    private boolean telnyxReady() {
        VoiceProperties.Sms sms = properties.getSms();
        return notBlank(sms.getTelnyxApiKey())
                && notBlank(sms.getTelnyxMessagingProfileId())
                && notBlank(sms.getFrom());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
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
