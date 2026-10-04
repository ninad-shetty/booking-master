package com.booking.bookingMaster.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReservationSeatId implements Serializable {

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "seat_id")
    private Long seatId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationSeatId)) return false;
        ReservationSeatId that = (ReservationSeatId) o;
        return Objects.equals(reservationId, that.reservationId) &&
               Objects.equals(seatId, that.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, seatId);
    }
}