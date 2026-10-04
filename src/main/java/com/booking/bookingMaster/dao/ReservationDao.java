package com.booking.bookingMaster.dao;

import com.booking.bookingMaster.model.Reservation;
import com.booking.bookingMaster.model.ReservationSeat;
import com.booking.bookingMaster.model.ReservationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.hibernate.LockOptions;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ReservationDao {
    @PersistenceContext private EntityManager em;

    public void save(Reservation r) { em.persist(r); }
    public void linkSeat(String reservationId, Long seatId) { em.persist(new ReservationSeat(reservationId, seatId)); }

    public Optional<Reservation> findById(String id) { return Optional.ofNullable(em.find(Reservation.class, id)); }

    public Optional<Reservation> findByIdForUpdate(String id) {
        return Optional.ofNullable(em.find(Reservation.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public List<String> seatNumbers(String reservationId) {
        return em.createQuery("SELECT s.seatNumber FROM Seat s WHERE s.id IN "
                        + "(SELECT rs.seatId FROM ReservationSeat rs WHERE rs.reservationId = :rid) ORDER BY s.id", String.class)
                .setParameter("rid", reservationId).getResultList();
    }

    /** Frees reservation_seats rows so the unique(seat_id) constraint allows the seat to be booked again. */
    public int deleteSeatLinks(String reservationId) {
        return em.createQuery("DELETE FROM ReservationSeat rs WHERE rs.reservationId = :rid")
                .setParameter("rid", reservationId).executeUpdate();
    }

    /** SKIP LOCKED so several app instances / the cancel endpoint never block each other. */
    public List<Reservation> findExpiredHeld(Instant cutoff, int limit) {
        return em.createQuery("SELECT r FROM Reservation r WHERE r.status = :st AND r.createdAt < :cutoff ORDER BY r.createdAt",
                        Reservation.class)
                .setParameter("st", ReservationStatus.HELD).setParameter("cutoff", cutoff)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", LockOptions.SKIP_LOCKED)
                .getResultList();
    }
}
