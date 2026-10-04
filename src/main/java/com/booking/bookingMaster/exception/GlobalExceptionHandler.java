package com.booking.bookingMaster.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.booking.bookingMaster.config.BookingMetrics;
import com.booking.bookingMaster.controller.Dtos.ErrorBody;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final BookingMetrics metrics;

    public GlobalExceptionHandler(BookingMetrics metrics) { this.metrics = metrics; }

    private ResponseEntity<ErrorBody> body(HttpStatus status, String code, String msg) {
        return ResponseEntity.status(status).body(new ErrorBody(code, msg));
    }

    @ExceptionHandler(SeatTakenException.class)
    ResponseEntity<ErrorBody> seatTaken(SeatTakenException e) { metrics.declined("seat_taken"); return body(HttpStatus.CONFLICT, "SEAT_TAKEN", e.getMessage()); }

    @ExceptionHandler(PerUserLimitExceededException.class)
    ResponseEntity<ErrorBody> limit(PerUserLimitExceededException e) { metrics.declined("per_user_limit"); return body(HttpStatus.CONFLICT, "PER_USER_LIMIT_EXCEEDED", e.getMessage()); }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorBody> idem(IdempotencyConflictException e) { metrics.declined("idempotency_conflict"); return body(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", e.getMessage()); }

    @ExceptionHandler(ReservationStateException.class)
    ResponseEntity<ErrorBody> state(ReservationStateException e) { return body(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", e.getMessage()); }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorBody> notFound(NotFoundException e) { metrics.declined("not_found"); return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage()); }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ErrorBody> bad(BadRequestException e) { return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage()); }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ErrorBody> unauth(UnauthorizedException e) { return body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage()); }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ErrorBody> forbidden(ForbiddenException e) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage()); }

    /** DB pool/connection exhaustion: explicit back-pressure signal instead of an anonymous 500. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ErrorBody> overloaded(Exception e) {
        log.warn("overloaded: {}", e.toString());
        metrics.declined("overloaded");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorBody("OVERLOADED", "temporarily overloaded, retry"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) {
        log.error("unexpected error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error");
    }
}
