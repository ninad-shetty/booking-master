package com.booking.bookingMaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookingMasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingMasterApplication.class, args);
	}

}
