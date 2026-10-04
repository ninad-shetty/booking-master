package com.booking.bookingMaster.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.booking.bookingMaster.model.ReservationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

/** JSON is snake_case globally (spring.jackson.property-naming-strategy=SNAKE_CASE). */
public final class Dtos {
    private Dtos() {}

    public record CreateShowRequest(@NotBlank String name, @NotEmpty List<String> seats,
                                    @JsonProperty("price_paise") @NotNull @Positive Integer pricePaise,
                                    @JsonProperty("per_user_limit") @NotNull @Positive Integer perUserLimit) {}
    public record ReserveRequest(@NotEmpty List<String> seats) {}
    public record CreateShowResponse(Long id, String name, int totalSeats, long pricePaise, int perUserLimit) {}
    public record SeatView(@JsonProperty("seat_number") String seatNumber, String status,
                           @JsonProperty("holder_user_id") String holderUserId) {}
    public record ShowView(Long id, String name, @JsonProperty("price_paise") long pricePaise,
                           @JsonProperty("per_user_limit") int perUserLimit,
                           @JsonProperty("total_seats") int totalSeats,
                           long available, long held, long confirmed, List<SeatView> seats) {}
    public record ReservationView(String id, Long showId, String userId, ReservationStatus status,
                                  long amountPaise, List<String> seats, Instant createdAt) {}
    public record ErrorBody(String error, String message) {}
}
