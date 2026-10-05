# Booking Master Technical Write-Up

## Summary

This project implements a PostgreSQL-backed ticket reservation API. Its strongest part is the reservation transaction: it uses idempotency storage, a per-user advisory lock, conditional seat updates, and database uniqueness constraints. The implementation is **not yet fully acceptance-ready**. Authentication is not cryptographically verified, readiness and metrics need work, automated tests do not cover the current API, the burst script is not in the public repository, and a successful Render deployment has not been confirmed.

## Reservation Correctness

### Atomic seat decision

`ReservationService.reserveSeats` is transactional. It normalizes requested seat numbers into a sorted, distinct set, checks the show's limit, verifies that every requested seat exists, and then claims each seat using a conditional JPQL update equivalent to:

```sql
UPDATE seats
SET status = 'HELD', holder_user_id = ?, held_until = ?
WHERE show_id = ? AND seat_number = ? AND status = 'AVAILABLE';
```

Only a request that updates one row wins a seat. PostgreSQL serializes competing updates to the same row; a later update rechecks the `AVAILABLE` predicate and affects zero rows. The service turns that result into `SeatTakenException`, which maps to HTTP 409. Reservation creation and seat links occur in the same transaction, so a failed claim for any requested seat rolls back earlier claims in that request.

For multi-seat requests, the service sorts seat numbers before claiming them, giving concurrent requests a consistent lock order and reducing deadlock risk. The policy is **all-or-nothing**: if any requested seat is unavailable or missing, none of the requested seats remain held.

The schema adds `UNIQUE (show_id, seat_number)` on seats and `UNIQUE (seat_id)` on `reservation_seats` as database-level backstops against duplicate seats and double booking.

### Per-user limit

Before claiming seats, the service takes a PostgreSQL transaction-scoped advisory lock derived from `(showId, userId)`, counts the user's active `HELD` and `CONFIRMED` seats, and checks the requested total against `per_user_limit`. This serializes the check-and-claim operation for one user/show pair.

Show creation currently requires `per_user_limit` in the request, even though the entity has a default of 4. The API therefore does not currently provide the specified default when that field is omitted.

### Idempotency

`IdempotencyKeyDao.tryInsert` inserts the key and canonical request JSON into PostgreSQL `idempotency_keys`, whose primary key makes each key unique. `ON CONFLICT (key) DO NOTHING` ensures concurrent requests using the same key cannot both become the first writer.

A retry loads the stored user, show, seat list, and reservation. The original reservation is replayed when all match; a different user, show, or seat list produces HTTP 409. Failed reservation transactions roll back their idempotency insert along with their seat claims.

### Holds and release

A successful reservation initially has status `HELD`; it is not immediately `CONFIRMED`. Holds use a 10-minute default duration. `ExpiryJob` runs at a 5-second fixed delay, finds `HELD` reservations older than the hold duration, releases their seats, removes seat links, and marks the reservations `EXPIRED`. Owners can also cancel their `HELD` or `CONFIRMED` reservations. Confirmed reservations are not expired automatically.

Known edge case: `confirm` releases an expired hold and then throws a runtime exception inside a `@Transactional` method. Spring rolls back transactions on runtime exceptions, so that particular release can roll back; the scheduled expiry job should later release the stale hold. This path should be changed to commit expiration before returning the conflict.

The expiry job logs only when it expires one or more holds or encounters an error. A quiet log does not prove that it failed.

### State and money

Money is represented in integer paise; no floating-point types are used. Show views derive `available`, `held`, and `confirmed` counts from seat rows and return individual seat summaries. The intended reconciliation is:

```text
available + held + confirmed == total_seats
```

The Create Show response is currently a compact summary and does not return every created seat in `AVAILABLE` state as the scenario's sample response requests. The subsequent Show GET does return seat summaries.

## Authentication and Security

Reservation identity is parsed from `Authorization: Bearer <user-id>`, and the request DTO contains no user ID field. Cancellation checks that the reservation belongs to that user. This prevents a separate body field from overriding the identity, but the bearer value is currently just a caller-supplied string; it is **not a verified JWT or authenticated credential**. Any caller can choose another user ID in the header. `SecurityConfig` permits all requests, so this is suitable only for a prototype, not production authentication.

Show creation uses `X-Admin-Token`. The local default is `admin`; Render is configured to generate `ADMIN_TOKEN`. Actuator endpoints are broadly exposed and the Spring Security chain permits requests, so actuator access should be restricted before production use.

## Health, Metrics, and Logs

- `GET /health` returns a constant `UP`; it does not check PostgreSQL. Render currently uses this path as its health check. There is no configured readiness check that fails when the database is down.
- Micrometer and the Prometheus registry are included. The `seats.available` gauge counts available seats across all shows. The `reservations.confirmed` counter is incremented when a new reservation is created, even though the reservation is initially `HELD`; the name/description is misleading. Decline counters cover seat-taken, per-user-limit, idempotency conflict, not-found, and overload reasons, but successful idempotent replays are not counted separately.
- Prometheus metrics are available through Actuator when the app is running (normally `/actuator/prometheus`). All Actuator endpoints are currently exposed; access should be limited.
- Error and expiry failures are logged, but logs are not structured with a request/correlation ID. There are no alert rules or recorded burst-log evidence in the repository. Render's service logs are available to workspace members, not as a public log feed.

## Deploy and Verification Status

The repository has a Java 21 multi-stage Dockerfile, a local Compose app/database stack, and a Render Blueprint defining a Docker web service plus PostgreSQL. The Docker entrypoint accepts Render PostgreSQL URL schemes, strips embedded URL credentials, and relies on separate database username/password environment variables. Local Compose has been verified to start the app and connect to PostgreSQL; a one-off container also verified the Render-style URL path against local Postgres.

A successful live Render deployment has **not** been verified. Render logs previously showed the app trying `localhost:5433`, which indicates missing environment variables or an old deployment. The Blueprint must be synced and deployed with `DB_URL`, `DB_USER`, and `DB_PASSWORD` available to the service. The free Render Postgres plan expires after 30 days.

The existing automated tests are not acceptance tests for the current API: `BookingControllerTest` still calls the old `/api/bookings` endpoints and old booking payload. It does not test reservation races, idempotency, ownership, expiry, or state reconciliation. A local `loadtest/burst_test.py` exists, but it is untracked and is **not included in the public Git repository**; the 20,000-request acceptance burst has not been verified here. There is no `WRITEUP.md` in the previously published commit; this file supplies that write-up.

## Consistency and Availability

PostgreSQL is the single source of truth. Seat decisions and reservation records are transactional, and there is no cache or fallback that grants seats while the database is unreachable. This favors consistency over availability: database outages prevent reservations, and some connection/pool failures map to HTTP 503. Unexpected failures can still map to 500, so zero 5xx under the specified burst is not established.

## What to Monitor / Page On

Before production, add alerts for sustained 5xx/503 rates, database connection-pool saturation, high reservation latency, stale `HELD` reservations past the hold duration, and repeated expiry-job failures. Reconcile `seats.available` and reservation counters against GET Show state; first correct the current counter semantics and add replay/decline breakdowns. Add a readiness health check backed by the datasource and request IDs in logs.

## AI Usage

An AI coding assistant was used iteratively to scaffold and modify application code, diagnose stack traces, adjust database/Docker/Render configuration, and draft documentation. The user supplied the target requirements, tested requests, deployment logs, and scope corrections, and reviewed or undid some edits. AI assistance contributed code and debugging suggestions; the current implementation and gaps described here reflect the checked repository state, not an independently certified result. The high-concurrency burst was not run as part of this write-up.

## Recommended Next Steps

1. Implement verified authentication (for example, validate signed tokens) and lock down Actuator endpoints.
2. Add DB-aware liveness/readiness endpoints and point Render's health check at readiness.
3. Fix expired-confirm transaction rollback and add a request default for `per_user_limit`.
4. Return a create-show response that includes its available seats if that response contract is required.
5. Correct reservation metrics, add idempotent replay metrics, and add request/correlation IDs.
6. Replace stale controller tests with database-backed tests for races, idempotency, limits, cancellation/expiry, and seat-count reconciliation.
7. Add the sanitized burst script to Git and run it against the deployed service; record the live URL and results.
8. Confirm Render Blueprint sync and a successful startup against its managed Postgres before calling the deployment production-ready.
