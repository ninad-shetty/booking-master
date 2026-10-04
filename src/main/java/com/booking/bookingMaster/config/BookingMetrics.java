package com.booking.bookingMaster.config;

import com.booking.bookingMaster.dao.SeatDao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {
    private final MeterRegistry registry;
    private final Counter confirmed;

    public BookingMetrics(MeterRegistry registry, SeatDao seatDao) {
        this.registry = registry;
        this.confirmed = Counter.builder("reservations.confirmed").description("Reservations accepted (201)").register(registry);
        Gauge.builder("seats.available", seatDao, d -> (double) d.countAvailable())
                .description("Seats currently AVAILABLE across all shows").register(registry);
    }

    public void confirmed() { confirmed.increment(); }
    public void declined(String reason) { registry.counter("reservations.declined", "reason", reason).increment(); }
}
