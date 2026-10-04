package com.booking.bookingMaster.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);
    private final ReservationService service;

    public ExpiryJob(ReservationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.expiry-interval-ms:5000}")
    public void run() {
        try {
            int n = service.expireStaleHolds();
            if (n > 0) log.info("expired {} stale holds", n);
        } catch (Exception e) {
            log.warn("expiry job failed: {}", e.toString());
        }
    }
}
