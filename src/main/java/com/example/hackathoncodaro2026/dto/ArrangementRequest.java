package com.example.hackathoncodaro2026.dto;

import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import com.example.hackathoncodaro2026.validation.DeceasedDatesValid;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@DeceasedDatesValid
public class ArrangementRequest {

    @NotNull
    private Long venueId;

    @NotNull
    private ServiceType serviceType;

    @NotNull
    private FuneralPackage funeralPackage;

    @NotBlank
    @Size(max = 120)
    private String deceasedFullName;

    @PastOrPresent
    private LocalDate dateOfBirth;

    @NotNull
    @PastOrPresent
    private LocalDate dateOfDeath;

    @NotNull
    @Min(1)
    private Integer attendees;

    @Pattern(regexp = "^$|^[+]?[0-9\\s().-]{7,20}$")
    @Size(max = 20)
    private String phone;

    @NotNull
    private PaymentMethod paymentMethod;

    @Size(max = 1000)
    private String note;

    private List<Long> extraIds = new ArrayList<>();

    @Size(max = 32)
    private String bookingSource;

    @Pattern(regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    @Size(max = 36)
    private String submissionToken;

    private LocalDateTime ceremonyStart;

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public FuneralPackage getFuneralPackage() {
        return funeralPackage;
    }

    public void setFuneralPackage(FuneralPackage funeralPackage) {
        this.funeralPackage = funeralPackage;
    }

    public String getDeceasedFullName() {
        return deceasedFullName;
    }

    public void setDeceasedFullName(String deceasedFullName) {
        this.deceasedFullName = deceasedFullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public LocalDate getDateOfDeath() {
        return dateOfDeath;
    }

    public void setDateOfDeath(LocalDate dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
    }

    public Integer getAttendees() {
        return attendees;
    }

    public void setAttendees(Integer attendees) {
        this.attendees = attendees;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<Long> getExtraIds() {
        return extraIds;
    }

    public void setExtraIds(List<Long> extraIds) {
        this.extraIds = extraIds;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource(String bookingSource) {
        this.bookingSource = bookingSource;
    }

    public String getSubmissionToken() {
        return submissionToken;
    }

    public void setSubmissionToken(String submissionToken) {
        this.submissionToken = submissionToken;
    }

    public LocalDateTime getCeremonyStart() {
        return ceremonyStart;
    }

    public void setCeremonyStart(LocalDateTime ceremonyStart) {
        this.ceremonyStart = ceremonyStart;
    }
}
