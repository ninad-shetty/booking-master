package com.booking.bookingMaster.service;

import com.booking.bookingMaster.model.*;
import com.booking.bookingMaster.service.ReserveOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.booking.bookingMaster.controller.Dtos.ReservationView;
import com.booking.bookingMaster.dao.*;
import com.booking.bookingMaster.exception.*;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {
    private final ShowDao showDao;
    private final SeatDao seatDao;
    private final ReservationDao reservationDao;
    private final IdempotencyKeyDao idempotencyDao;
    private final ObjectMapper mapper;
    private final Duration holdDuration;

    public ReservationService(ShowDao showDao, SeatDao seatDao, ReservationDao reservationDao,
                              IdempotencyKeyDao idempotencyDao, ObjectMapper mapper,
                              @Value("${app.hold-minutes:10}") long holdMinutes) {
        this.showDao = showDao; this.seatDao = seatDao; this.reservationDao = reservationDao;
        this.idempotencyDao = idempotencyDao; this.mapper = mapper; this.holdDuration = Duration.ofMinutes(holdMinutes);
    }

    @Transactional
    public ReserveOutcome reserveSeats(Long showId, String userId, List<String> seatNumbers, String idempotencyKey) {
        // Sorted + distinct: a canonical request AND a global lock order (no deadlocks between multi-seat requests).
        SortedSet<String> requested = new TreeSet<>();
        for (String s : seatNumbers) {
            if (s == null || s.isBlank()) throw new BadRequestException("seat numbers must be non-blank");
            requested.add(s.trim());
        }
        if (requested.isEmpty()) throw new BadRequestException("seats must not be empty");

        Show show = showDao.findById(showId).orElseThrow(() -> new NotFoundException("show " + showId + " not found"));
        if (requested.size() > show.getPerUserLimit())
            throw new PerUserLimitExceededException("limit is " + show.getPerUserLimit() + " seats per user");

        // 1) Idempotency: first writer of the key wins; concurrent duplicates block here until it commits/rolls back.
        String reservationId = UUID.randomUUID().toString();
        if (!idempotencyDao.tryInsert(idempotencyKey, userId, showId, reservationId, toJson(requested))) {
            return replay(idempotencyKey, userId, showId, requested);
        }

        // 2) Per-user limit: advisory lock makes check-then-claim atomic for this (show, user).
        seatDao.lockUserShow(showId, userId);
        long active = seatDao.countActiveForUser(showId, userId);
        if (active + requested.size() > show.getPerUserLimit())
            throw new PerUserLimitExceededException("user already holds " + active + " seat(s); limit is " + show.getPerUserLimit());

        // 3) All requested seats must exist.
        List<Seat> found = seatDao.findByShowAndNumbers(showId, requested);
        if (found.size() != requested.size()) {
            Set<String> have = new HashSet<>();
            found.forEach(s -> have.add(s.getSeatNumber()));
            Set<String> missing = new TreeSet<>(requested);
            missing.removeAll(have);
            throw new NotFoundException("unknown seat(s): " + missing);
        }

        // 4) Atomic claim, in sorted order. Any loss throws => whole tx rolls back => no partial holds.
        Instant heldUntil = Instant.now().plus(holdDuration);
        for (String seatNumber : requested) {
            int rows = seatDao.tryClaimSeat(showId, seatNumber, SeatStatus.AVAILABLE, SeatStatus.HELD, userId, heldUntil);
            if (rows == 0) throw new SeatTakenException("seat " + seatNumber + " is not available");
        }

        // 5) Persist reservation + links. unique(seat_id) is the DB-level backstop against double booking.
        Reservation reservation = new Reservation(reservationId, showId, userId, ReservationStatus.HELD,
                show.getPricePaise() * requested.size());
        reservationDao.save(reservation);
        for (Seat s : found) reservationDao.linkSeat(reservationId, s.getId());

        return new ReserveOutcome(view(reservation, new ArrayList<>(requested)), true);
    }

    private ReserveOutcome replay(String key, String userId, Long showId, SortedSet<String> requested) {
        IdempotencyKey stored = idempotencyDao.find(key)
                .orElseThrow(() -> new IdempotencyConflictException("idempotency key is in use"));
        Set<String> storedSeats = new HashSet<>();
        Object raw = stored.getRequestBody().get("seats");
        if (raw instanceof Collection<?>) ((Collection<?>) raw).forEach(o -> storedSeats.add(String.valueOf(o)));
        if (!stored.getUserId().equals(userId) || !stored.getShowId().equals(showId) || !storedSeats.equals(requested))
            throw new IdempotencyConflictException("idempotency key was already used with a different request");
        Reservation r = reservationDao.findById(stored.getReservationId())
                .orElseThrow(() -> new IdempotencyConflictException("idempotency key is in use"));
        return new ReserveOutcome(view(r, reservationDao.seatNumbers(r.getId())), false);
    }

    @Transactional
    public ReservationView confirm(String reservationId, String userId) {
        Reservation r = lockOwned(reservationId, userId);
        if (r.getStatus() == ReservationStatus.CONFIRMED) return view(r, reservationDao.seatNumbers(r.getId()));
        if (r.getStatus() != ReservationStatus.HELD) throw new ReservationStateException("reservation is " + r.getStatus());
        if (r.getCreatedAt().plus(holdDuration).isBefore(Instant.now())) {
            release(r, ReservationStatus.EXPIRED);
            throw new ReservationStateException("hold expired");
        }
        seatDao.confirmByReservation(reservationId);
        r.setStatus(ReservationStatus.CONFIRMED);
        return view(r, reservationDao.seatNumbers(r.getId()));
    }

    @Transactional
    public ReservationView cancel(String reservationId, String userId) {
        Reservation r = lockOwned(reservationId, userId);
        List<String> seats = reservationDao.seatNumbers(reservationId);
        if (r.getStatus() == ReservationStatus.HELD || r.getStatus() == ReservationStatus.CONFIRMED) {
            release(r, ReservationStatus.CANCELLED);
        }
        return view(r, seats);
    }

    /** Called by ExpiryJob. Returns number of reservations expired. */
    @Transactional
    public int expireStaleHolds() {
        List<Reservation> stale = reservationDao.findExpiredHeld(Instant.now().minus(holdDuration), 500);
        for (Reservation r : stale) release(r, ReservationStatus.EXPIRED);
        return stale.size();
    }

    private void release(Reservation r, ReservationStatus newStatus) {
        seatDao.releaseByReservation(r.getId());
        reservationDao.deleteSeatLinks(r.getId());
        r.setStatus(newStatus);
    }

    private Reservation lockOwned(String reservationId, String userId) {
        Reservation r = reservationDao.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("reservation not found"));
        if (!r.getUserId().equals(userId)) throw new ForbiddenException("reservation belongs to another user");
        return r;
    }

    private ReservationView view(Reservation r, List<String> seats) {
        return new ReservationView(r.getId(), r.getShowId(), r.getUserId(), r.getStatus(), r.getAmountPaise(), seats, r.getCreatedAt());
    }

    private String toJson(SortedSet<String> seats) {
        try { return mapper.writeValueAsString(Map.of("seats", seats)); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
