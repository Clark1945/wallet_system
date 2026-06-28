-- Convert event-time columns from TIMESTAMP (without time zone) to TIMESTAMPTZ so they store a
-- true absolute instant (UTC), matching the entities' switch from LocalDateTime to Instant.
-- ("store UTC absolute, display local" — display-time zone conversion happens in the app.)
--
-- Existing values were written while the app ran in UTC, i.e. they are UTC wall-clock numbers.
-- `USING <col> AT TIME ZONE 'UTC'` reinterprets each naive value AS UTC and yields the correct
-- timestamptz instant. This is a RE-TYPING, not a data shift: the absolute moment is unchanged
-- (00:00 naive-UTC -> 00:00Z, which renders as 08:00 in Asia/Taipei).
--
-- Postgres rebuilds the idx_tx_created_at index automatically as part of the type change.

ALTER TABLE transactions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE members
    ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'UTC',
    ALTER COLUMN last_login_at TYPE TIMESTAMPTZ USING last_login_at AT TIME ZONE 'UTC';
