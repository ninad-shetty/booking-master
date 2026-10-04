package com.booking.bookingMaster.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private String id; // UUID as String

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.HELD;

    @Column(nullable = false)
    private Integer amountPaise;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;


    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    protected Reservation() {}
    public Reservation(String id, Long showId, String userId, ReservationStatus status, int amountPaise) {
        this.id = id; this.showId = showId; this.userId = userId; this.status = status;
        this.amountPaise = amountPaise; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    // getters/setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Integer getAmountPaise() {
        return amountPaise;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }


    public Instant getUpdatedAt() {
        return updatedAt;
    }


}