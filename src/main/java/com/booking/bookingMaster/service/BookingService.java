package com.booking.bookingMaster.service;

import com.booking.bookingMaster.model.Booking;
import com.booking.bookingMaster.model.BookingRequest;
import com.booking.bookingMaster.model.BookingStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private final List<Booking> bookings = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    public List<Booking> getAllBookings() {
        return bookings;
    }

    public Booking getBooking(Long id) {
        return bookings.stream()
                .filter(booking -> booking.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Booking not found with id: " + id));
    }

    public Booking createBooking(BookingRequest request) {
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (request.getEventName() == null || request.getEventName().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        if (request.getTicketCount() <= 0) {
            throw new IllegalArgumentException("Ticket count must be greater than zero");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ticket price must be positive");
        }

        Booking booking = new Booking(
                idGenerator.getAndIncrement(),
                request.getCustomerName(),
                request.getEventName(),
                request.getTicketCount(),
                request.getPrice(),
                BookingStatus.CONFIRMED
        );
        bookings.add(booking);
        return booking;
    }

    public Booking cancelBooking(Long id) {
        Booking booking = getBooking(id);
        booking.setStatus(BookingStatus.CANCELLED);
        return booking;
    }
}
