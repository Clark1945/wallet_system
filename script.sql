-- ============================================================
--  Wallet System — DDL Script
--  適用 PostgreSQL 14+
--  執行方式：psql -U postgres -d wallet_db -f script.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS members (
    id       UUID         PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    age      INTEGER      NOT NULL DEFAULT 0,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallets (
    id        UUID           PRIMARY KEY,
    member_id UUID           NOT NULL UNIQUE REFERENCES members(id),
    balance   NUMERIC(19, 2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS transactions (
    id               UUID           PRIMARY KEY,
    from_wallet_id   UUID,
    to_wallet_id     UUID,
    type             VARCHAR(20)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    description      VARCHAR(255),
    created_at       TIMESTAMP      NOT NULL
);
