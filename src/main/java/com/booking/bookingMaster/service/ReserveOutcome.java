package com.booking.bookingMaster.service;

import com.booking.bookingMaster.controller.Dtos.ReservationView;

public record ReserveOutcome(ReservationView reservation, boolean created) {}
