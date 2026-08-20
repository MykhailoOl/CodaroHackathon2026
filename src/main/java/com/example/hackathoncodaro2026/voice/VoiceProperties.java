package com.example.hackathoncodaro2026.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.voice")
public class VoiceProperties {

    private boolean enabled = true;
    private String toolWebhookSecret = "change-me-tool-webhook-secret";
    private String publicBaseUrl = "http://localhost:8080";
    private String timezone = "Europe/Warsaw";
    private int defaultDurationHours = 1;
    private int maxSlots = 5;
    private ElevenLabs elevenlabs = new ElevenLabs();
    private Sms sms = new Sms();
    private Telephony telephony = new Telephony();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToolWebhookSecret() {
        return toolWebhookSecret;
    }

    public void setToolWebhookSecret(String toolWebhookSecret) {
        this.toolWebhookSecret = toolWebhookSecret;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getDefaultDurationHours() {
        return defaultDurationHours;
    }

    public void setDefaultDurationHours(int defaultDurationHours) {
        this.defaultDurationHours = defaultDurationHours;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public ElevenLabs getElevenlabs() {
        return elevenlabs;
    }

    public void setElevenlabs(ElevenLabs elevenlabs) {
        this.elevenlabs = elevenlabs;
    }

    public Sms getSms() {
        return sms;
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public Telephony getTelephony() {
        return telephony;
    }

    public void setTelephony(Telephony telephony) {
        this.telephony = telephony;
    }

    public static class ElevenLabs {
        private String apiKey = "";
        private String agentId = "";
        private String voiceId = "";
        private String phoneNumberId = "";
        private String webhookSecret = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public String getVoiceId() {
            return voiceId;
        }

        public void setVoiceId(String voiceId) {
            this.voiceId = voiceId;
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        public void setPhoneNumberId(String phoneNumberId) {
            this.phoneNumberId = phoneNumberId;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class Sms {
        private boolean enabled = true;
        private String provider = "log";
        private String from = "Courtly";
        private String telnyxApiKey = "";
        private String telnyxMessagingProfileId = "";
        private String smsapiToken = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTelnyxApiKey() {
            return telnyxApiKey;
        }

        public void setTelnyxApiKey(String telnyxApiKey) {
            this.telnyxApiKey = telnyxApiKey;
        }

        public String getTelnyxMessagingProfileId() {
            return telnyxMessagingProfileId;
        }

        public void setTelnyxMessagingProfileId(String telnyxMessagingProfileId) {
            this.telnyxMessagingProfileId = telnyxMessagingProfileId;
        }

        public String getSmsapiToken() {
            return smsapiToken;
        }

        public void setSmsapiToken(String smsapiToken) {
            this.smsapiToken = smsapiToken;
        }
    }

    public static class Telephony {
        private String provider = "none";
        private String twilioAccountSid = "";
        private String twilioAuthToken = "";
        private String telnyxPublicKey = "";
        private String telnyxFqdnConnectionId = "";
        private String telnyxTexmlAppId = "";
        private String sipTerminationUri = "sip:sip.rtc.elevenlabs.io:5060;transport=tcp";
        private String sipUsername = "";
        private String sipPassword = "";
        private String sipFromNumber = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getTwilioAccountSid() {
            return twilioAccountSid;
        }

        public void setTwilioAccountSid(String twilioAccountSid) {
            this.twilioAccountSid = twilioAccountSid;
        }

        public String getTwilioAuthToken() {
            return twilioAuthToken;
        }

        public void setTwilioAuthToken(String twilioAuthToken) {
            this.twilioAuthToken = twilioAuthToken;
        }

        public String getTelnyxPublicKey() {
            return telnyxPublicKey;
        }

        public void setTelnyxPublicKey(String telnyxPublicKey) {
            this.telnyxPublicKey = telnyxPublicKey;
        }

        public String getTelnyxFqdnConnectionId() {
            return telnyxFqdnConnectionId;
        }

        public void setTelnyxFqdnConnectionId(String telnyxFqdnConnectionId) {
            this.telnyxFqdnConnectionId = telnyxFqdnConnectionId;
        }

        public String getTelnyxTexmlAppId() {
            return telnyxTexmlAppId;
        }

        public void setTelnyxTexmlAppId(String telnyxTexmlAppId) {
            this.telnyxTexmlAppId = telnyxTexmlAppId;
        }

        public String getSipTerminationUri() {
            return sipTerminationUri;
        }

        public void setSipTerminationUri(String sipTerminationUri) {
            this.sipTerminationUri = sipTerminationUri;
        }

        public String getSipUsername() {
            return sipUsername;
        }

        public void setSipUsername(String sipUsername) {
            this.sipUsername = sipUsername;
        }

        public String getSipPassword() {
            return sipPassword;
        }

        public void setSipPassword(String sipPassword) {
            this.sipPassword = sipPassword;
        }

        public String getSipFromNumber() {
            return sipFromNumber;
        }

        public void setSipFromNumber(String sipFromNumber) {
            this.sipFromNumber = sipFromNumber;
        }

        public boolean sipConfigured() {
            return notBlank(sipUsername) && notBlank(sipPassword) && notBlank(sipFromNumber);
        }

        private boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
