package com.booking.bookingMaster.model;


import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "reservation_seats")
@IdClass(ReservationSeatId.class)
public class ReservationSeat {
    @Id @Column(name = "reservation_id", length = 36)
    private String reservationId;
    @Id @Column(name = "seat_id")
    private Long seatId;

    protected ReservationSeat() {}
    public ReservationSeat(String reservationId, Long seatId) { this.reservationId = reservationId; this.seatId = seatId; }
    public String getReservationId() { return reservationId; }
    public Long getSeatId() { return seatId; }
}
