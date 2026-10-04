package com.booking.bookingMaster.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    private String key;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private String reservationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", nullable = false)
    private Map<String, Object> requestBody; // store canonical JSON, e.g. ["A12","A13"]

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    protected IdempotencyKey() {}

    // getters/setters

    public String getKey() {
        return key;
    }

    public String getUserId() {
        return userId;
    }

    public Long getShowId() {
        return showId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public Map<String, Object> getRequestBody() {
        return requestBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}