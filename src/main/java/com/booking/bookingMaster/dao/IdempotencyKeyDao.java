package com.booking.bookingMaster.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.booking.bookingMaster.model.IdempotencyKey;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class IdempotencyKeyDao {
    @PersistenceContext
    private EntityManager em;

    public boolean tryInsert(String key, String userId, Long showId, String reservationId, String requestBodyJson) {
        int n = em.createNativeQuery(
                "INSERT INTO idempotency_keys (key, user_id, show_id, reservation_id, request_body, created_at) "
                        + "VALUES (:key, :userId, :showId, :rid, CAST(:body AS jsonb), now()) ON CONFLICT (key) DO NOTHING")
                .setParameter("key", key).setParameter("userId", userId).setParameter("showId", showId)
                .setParameter("rid", reservationId).setParameter("body", requestBodyJson).executeUpdate();
        return n == 1;
    }

    public Optional<IdempotencyKey> find(String key) {
        return Optional.ofNullable(em.find(IdempotencyKey.class, key));
    }

}