package com.booking.bookingMaster.dao;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.booking.bookingMaster.model.Seat;
import com.booking.bookingMaster.model.SeatStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Repository
public class SeatDao {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void saveAll(List<Seat> seats) {
        for (Seat seat : seats) {
            em.persist(seat);
        }
    }

    public List<Seat> findByShowId(Long showId) {
        TypedQuery<Seat> q = em.createQuery(
                "SELECT s FROM Seat s WHERE s.show.id = :showId", Seat.class);
        q.setParameter("showId", showId);
        return q.getResultList();
    }

    public List<Seat> findByShowIds(Collection<Long> showIds) {
        if (showIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT s FROM Seat s WHERE s.show.id IN :showIds ORDER BY s.show.id, s.id", Seat.class)
                .setParameter("showIds", showIds)
                .getResultList();
    }

    public Optional<Seat> findByShowIdAndSeatNumber(Long showId, String seatNumber) {
        TypedQuery<Seat> q = em.createQuery(
                "SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber = :seatNumber", Seat.class);
        q.setParameter("showId", showId);
        q.setParameter("seatNumber", seatNumber);
        return q.getResultList().stream().findFirst();
    }

    @Transactional
    public int tryClaimSeat(
            Long showId,
            String seatNumber,
            SeatStatus currentStatus,
            SeatStatus newStatus,
            String userId,
            Instant heldUntil) {
        var q = em.createQuery("""
                    UPDATE Seat s
                    SET s.status = :newStatus,
                        s.holderUserId = :userId,
                        s.heldUntil = :heldUntil
                    WHERE s.show.id = :showId
                      AND s.seatNumber = :seatNumber
                      AND s.status = :currentStatus
                """);
        q.setParameter("newStatus", newStatus);
        q.setParameter("userId", userId);
        q.setParameter("heldUntil", heldUntil);
        q.setParameter("showId", showId);
        q.setParameter("seatNumber", seatNumber);
        q.setParameter("currentStatus", currentStatus);
        return q.executeUpdate();
    }

    public long countByShowIdAndHolderUserIdAndStatusIn(
            Long showId,
            String userId,
            java.util.Set<SeatStatus> statuses) {
        TypedQuery<Long> q = em.createQuery(
                """
                        SELECT COUNT(s)
                        FROM Seat s
                        WHERE s.show.id = :showId
                          AND s.holderUserId = :userId
                          AND s.status IN :statuses
                        """, Long.class);
        q.setParameter("showId", showId);
        q.setParameter("userId", userId);
        q.setParameter("statuses", statuses);
        return q.getSingleResult();
    }

    public List<Seat> findByShowAndNumbers(Long showId, Collection<String> numbers) {
        return em.createQuery("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN (:nums)", Seat.class)
                .setParameter("showId", showId).setParameter("nums", numbers).getResultList();
    }

    public long countActiveForUser(Long showId, String userId) {
        return em.createQuery("SELECT count(s) FROM Seat s WHERE s.show.id = :showId AND s.holderUserId = :userId "
                        + "AND s.status IN (:statuses)", Long.class)
                .setParameter("showId", showId).setParameter("userId", userId)
                .setParameter("statuses", List.of(SeatStatus.HELD, SeatStatus.CONFIRMED)).getSingleResult();
    }

    public long countAvailable() {
        return em.createQuery("SELECT count(s) FROM Seat s WHERE s.status = :st", Long.class)
                .setParameter("st", SeatStatus.AVAILABLE).getSingleResult();
    }

    public int confirmByReservation(String reservationId) {
        return em.createNativeQuery("UPDATE seats SET status = 'CONFIRMED', held_until = NULL, updated_at = now() "
                        + "WHERE id IN (SELECT seat_id FROM reservation_seats WHERE reservation_id = :rid)")
                .setParameter("rid", reservationId).executeUpdate();
    }


    public int releaseByReservation(String reservationId) {
        return em.createNativeQuery("UPDATE seats SET status = 'AVAILABLE', holder_user_id = NULL, held_until = NULL, "
                        + "updated_at = now() WHERE id IN (SELECT seat_id FROM reservation_seats WHERE reservation_id = :rid)")
                .setParameter("rid", reservationId).executeUpdate();
    }


    /** Transaction-scoped advisory lock: serializes the limit-check + claim for one (show, user). */
    public void lockUserShow(Long showId, String userId) {
        long key = showId * 1_000_003L + userId.hashCode();
        em.createNativeQuery("SELECT count(*) FROM (SELECT pg_advisory_xact_lock(:k)) t")
                .setParameter("k", key).getSingleResult();
    }

}