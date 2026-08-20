package com.example.hackathoncodaro2026.voice.dto;

import java.util.UUID;

public class CreateBookingResponse {

    private String requestId = UUID.randomUUID().toString();
    private String bookingId;
    private String confirmationLine;
    private String smsStatus;
    private String inviteUrl;

    public CreateBookingResponse() {
    }

    public CreateBookingResponse(String bookingId, String confirmationLine, String smsStatus, String inviteUrl) {
        this.bookingId = bookingId;
        this.confirmationLine = confirmationLine;
        this.smsStatus = smsStatus;
        this.inviteUrl = inviteUrl;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getConfirmationLine() {
        return confirmationLine;
    }

    public void setConfirmationLine(String confirmationLine) {
        this.confirmationLine = confirmationLine;
    }

    public String getSmsStatus() {
        return smsStatus;
    }

    public void setSmsStatus(String smsStatus) {
        this.smsStatus = smsStatus;
    }

    public String getInviteUrl() {
        return inviteUrl;
    }

    public void setInviteUrl(String inviteUrl) {
        this.inviteUrl = inviteUrl;
    }
}
