package com.booking.bookingMaster.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.booking.bookingMaster.model.Show;


public interface ShowDao extends JpaRepository<Show, Long> {
    // Implement your data access methods for Show entity here

}