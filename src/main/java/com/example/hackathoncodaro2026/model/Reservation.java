package com.example.hackathoncodaro2026.model;

import com.example.hackathoncodaro2026.model.enums.FuneralPackage;
import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
import com.example.hackathoncodaro2026.model.enums.ServiceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(name = "uk_reservations_submission_token", columnNames = "submission_token"))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private ServiceVenue venue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "service_type", nullable = false, length = 32)
    private ServiceType serviceType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "package_code", nullable = false, length = 32)
    private FuneralPackage funeralPackage;

    @NotBlank
    @Size(max = 120)
    @Column(name = "deceased_full_name", nullable = false, length = 120)
    private String deceasedFullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @NotNull
    @PastOrPresent
    @Column(name = "date_of_death", nullable = false)
    private LocalDate dateOfDeath;

    @Min(1)
    @Column(nullable = false)
    private int attendees = 1;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'CASH'")
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @ColumnDefault("0")
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<ReservationExtra> extras = new LinkedHashSet<>();

    @Size(max = 1000)
    @Column(length = 1000)
    private String note;

    @Size(max = 40)
    @Column(name = "cancellation_reason", length = 40)
    private String cancellationReason;

    @Size(max = 36)
    @Column(name = "submission_token", unique = true, length = 36)
    private String submissionToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = ReservationStatus.PENDING;
        }
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (attendees < 1) {
            attendees = 1;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @AssertTrue
    public boolean isPeriodValid() {
        if (startAt == null || endAt == null) {
            return true;
        }
        return endAt.isAfter(startAt);
    }

    public String getFormattedTotalAmount() {
        return totalAmount == null ? "0.00 PLN" : totalAmount.setScale(2, RoundingMode.HALF_UP) + " PLN";
    }

    public String getExtrasSummary() {
        if (extras == null || extras.isEmpty()) {
            return "";
        }
        return extras.stream().map(ReservationExtra::getDisplayLabel).collect(Collectors.joining(", "));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ServiceVenue getVenue() {
        return venue;
    }

    public void setVenue(ServiceVenue venue) {
        this.venue = venue;
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

    public int getAttendees() {
        return attendees;
    }

    public void setAttendees(int attendees) {
        this.attendees = attendees;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO.setScale(2) : totalAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public Set<ReservationExtra> getExtras() {
        return extras;
    }

    public void setExtras(Set<ReservationExtra> extras) {
        this.extras = extras;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getSubmissionToken() {
        return submissionToken;
    }

    public void setSubmissionToken(String submissionToken) {
        this.submissionToken = submissionToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reservation that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
