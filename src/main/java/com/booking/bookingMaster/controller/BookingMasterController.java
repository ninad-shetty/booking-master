package com.booking.bookingMaster.controller;

import com.booking.bookingMaster.config.BookingMetrics;
import com.booking.bookingMaster.controller.Dtos.CreateShowRequest;
import com.booking.bookingMaster.controller.Dtos.CreateShowResponse;
import com.booking.bookingMaster.controller.Dtos.ReservationView;
import com.booking.bookingMaster.controller.Dtos.ReserveRequest;
import com.booking.bookingMaster.controller.Dtos.ShowView;
import com.booking.bookingMaster.exception.BadRequestException;
import com.booking.bookingMaster.exception.ForbiddenException;
import com.booking.bookingMaster.exception.UnauthorizedException;
import com.booking.bookingMaster.model.Reservation;
import com.booking.bookingMaster.model.Seat;
import com.booking.bookingMaster.service.ReservationService;
import com.booking.bookingMaster.service.ReserveOutcome;
import com.booking.bookingMaster.service.ShowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.booking.bookingMaster.model.Show;


@RestController
public class BookingMasterController {


    private final ShowService showService;
    private final ReservationService reservationService;
    private final BookingMetrics metrics;
    private final String adminToken;

    public BookingMasterController(ShowService showService, ReservationService reservationService,
                                        BookingMetrics metrics, @Value("${app.admin-token}") String adminToken)
    {
        this.showService = showService;
        this.reservationService = reservationService;
        this.metrics = metrics;
        this.adminToken = adminToken;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping({"/getAllShows", "/shows"})
    public List<ShowView> getAllShows() {
        return showService.getAllShowViews();
    }

    @GetMapping("/shows/{showId}")
    public ShowView getShow(@PathVariable Long showId) {
        return showService.getShowView(showId);
    }

    @PostMapping("/shows")
    public ResponseEntity<CreateShowResponse> createShow(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody CreateShowRequest request) {
        if (token == null || !token.equals(adminToken)) throw new ForbiddenException("admin token required (X-Admin-Token)");
        Show createdShow = showService.createShow(
                request.name(),
                request.seats(),
                request.pricePaise(),
                request.perUserLimit()
        );
        CreateShowResponse response = new CreateShowResponse(
                createdShow.getId(),
                createdShow.getName(),
                createdShow.getTotalSeats(),
                createdShow.getPricePaise(),
                createdShow.getPerUserLimit()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<ReservationView> reserve(@PathVariable Long id,
                                                   @RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                                   @Valid @RequestBody ReserveRequest req) {
        String userId = userId(auth);
        if (idemKey == null || idemKey.isBlank() || idemKey.length() > 128)
            throw new BadRequestException("Idempotency-Key header is required (max 128 chars)");
        ReserveOutcome out = reservationService.reserveSeats(id, userId, req.seats(), idemKey.trim());
        if (out.created()) {
            metrics.confirmed();
            return ResponseEntity.status(HttpStatus.CREATED).body(out.reservation());
        }
        return ResponseEntity.ok(out.reservation());
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationView confirm(@PathVariable String id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return reservationService.confirm(id, userId(auth));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationView cancel(@PathVariable String id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return reservationService.cancel(id, userId(auth));
    }

    private static String userId(String auth) {
        if (auth == null || auth.length() <= 7 || !auth.regionMatches(true, 0, "Bearer ", 0, 7))
            throw new UnauthorizedException("Authorization: Bearer <user-id> required");
        String id = auth.substring(7).trim();
        if (id.isEmpty() || id.length() > 64) throw new UnauthorizedException("invalid user id");
        return id;
    }



}
