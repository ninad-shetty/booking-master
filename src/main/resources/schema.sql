-- Shows
CREATE TABLE IF NOT EXISTS shows (
    id             BIGSERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    total_seats    INT NOT NULL,
    price_paise          INT NOT NULL,            -- paise
    per_user_limit INT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- seats
CREATE TABLE IF NOT EXISTS seats (
    id              BIGSERIAL PRIMARY KEY,
    show_id         BIGINT NOT NULL REFERENCES shows(id),
    seat_number     TEXT NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('AVAILABLE','HELD','CONFIRMED')),
    holder_user_id  TEXT NULL,
    held_until      TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (show_id, seat_number)
);
CREATE INDEX IF NOT EXISTS idx_seats_show_seat   ON seats(show_id, seat_number);
CREATE INDEX IF NOT EXISTS idx_seats_show_status ON seats(show_id, status);

-- reservations
CREATE TABLE IF NOT EXISTS reservations (
    id            TEXT PRIMARY KEY,
    show_id       BIGINT NOT NULL REFERENCES shows(id),
    user_id       TEXT NOT NULL,
    status        TEXT NOT NULL CHECK (status IN ('HELD','CONFIRMED','CANCELLED','EXPIRED')),
    amount_paise  INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- used by the hold-expiry job
CREATE INDEX IF NOT EXISTS idx_reservations_status_created ON reservations(status, created_at);

-- reservation_seats
CREATE TABLE IF NOT EXISTS reservation_seats (
    reservation_id TEXT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_id        BIGINT NOT NULL REFERENCES seats(id),
    PRIMARY KEY (reservation_id, seat_id),
    UNIQUE (seat_id)
);

-- idempotency_keys
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key            TEXT PRIMARY KEY,
    user_id        TEXT NOT NULL,
    show_id        BIGINT NOT NULL,
    reservation_id TEXT NOT NULL REFERENCES reservations(id) DEFERRABLE INITIALLY DEFERRED,
    request_body   JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
