package com.booking.bookingMaster.model;

import java.math.BigDecimal;

public class Booking {

    private Long id;
    private String customerName;
    private String eventName;
    private int ticketCount;
    private BigDecimal price;
    private BookingStatus status;

    public Booking() {
    }

    public Booking(Long id, String customerName, String eventName, int ticketCount, BigDecimal price, BookingStatus status) {
        this.id = id;
        this.customerName = customerName;
        this.eventName = eventName;
        this.ticketCount = ticketCount;
        this.price = price;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
