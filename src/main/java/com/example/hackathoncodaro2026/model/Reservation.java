package com.example.hackathoncodaro2026.model;

import com.example.hackathoncodaro2026.model.enums.PaymentMethod;
import com.example.hackathoncodaro2026.model.enums.ReservationKind;
import com.example.hackathoncodaro2026.model.enums.ReservationStatus;
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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "reservations")
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
    @JoinColumn(name = "resource_id", nullable = false)
    private SportResource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private User coach;

    @Size(max = 32)
    @Column(name = "skill_level", length = 32)
    private String skillLevel;

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

    @Min(1)
    @ColumnDefault("1")
    @Column(name = "party_size", nullable = false)
    private int partySize = 1;

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'STANDARD'")
    @Column(name = "kind", nullable = false, length = 32)
    private ReservationKind kind = ReservationKind.STANDARD;

    @Min(1)
    @ColumnDefault("1")
    @Column(name = "occupancy_units", nullable = false)
    private int occupancyUnits = 1;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<ReservationExtra> extras = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Size(max = 300)
    @Column(length = 300)
    private String note;

    @Size(max = 40)
    @Column(name = "invite_token", unique = true, length = 40)
    private String inviteToken;

    @Size(max = 254)
    @Column(name = "invite_email", length = 254)
    private String inviteEmail;

    @Column(name = "invite_sent_at")
    private Instant inviteSentAt;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    public Reservation() {
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ReservationStatus.PENDING;
        }
        if (partySize < 1) {
            partySize = 1;
        }
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO.setScale(2);
        }
        if (kind == null) {
            kind = ReservationKind.STANDARD;
        }
        if (occupancyUnits < 1) {
            occupancyUnits = 1;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
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

    public SportResource getResource() {
        return resource;
    }

    public void setResource(SportResource resource) {
        this.resource = resource;
    }

    public User getCoach() {
        return coach;
    }

    public void setCoach(User coach) {
        this.coach = coach;
    }

    public String getSkillLevel() {
        return skillLevel;
    }

    public void setSkillLevel(String skillLevel) {
        this.skillLevel = skillLevel;
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

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
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
        this.totalAmount = totalAmount;
    }

    public String getPartySizeLabel() {
        if (resource == null) {
            return String.valueOf(partySize);
        }
        return resource.partySizeLabel(partySize, kind);
    }

    public String getFormattedTotalAmount() {
        BigDecimal amount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " PLN";
    }

    public ReservationKind getKind() {
        return kind;
    }

    public void setKind(ReservationKind kind) {
        this.kind = kind;
    }

    public int getOccupancyUnits() {
        return occupancyUnits;
    }

    public void setOccupancyUnits(int occupancyUnits) {
        this.occupancyUnits = occupancyUnits;
    }

    public Set<ReservationExtra> getExtras() {
        return extras;
    }

    public void setExtras(Set<ReservationExtra> extras) {
        this.extras = extras;
    }

    public void addExtra(ReservationExtra extra) {
        extras.add(extra);
        extra.setReservation(this);
    }

    public boolean isLesson() {
        return kind == ReservationKind.LESSON;
    }

    public String getKindLabel() {
        return kind == null ? ReservationKind.STANDARD.getLabel() : kind.getLabel();
    }

    public boolean hasExtras() {
        return extras != null && !extras.isEmpty();
    }

    public String getExtrasSummary() {
        if (!hasExtras()) {
            return "";
        }
        List<String> labels = extras.stream()
                .map(ReservationExtra::getDisplayLabel)
                .collect(Collectors.toCollection(ArrayList::new));
        return String.join(", ", labels);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }

    public String getInviteEmail() {
        return inviteEmail;
    }

    public void setInviteEmail(String inviteEmail) {
        this.inviteEmail = inviteEmail;
    }

    public Instant getInviteSentAt() {
        return inviteSentAt;
    }

    public void setInviteSentAt(Instant inviteSentAt) {
        this.inviteSentAt = inviteSentAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
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
