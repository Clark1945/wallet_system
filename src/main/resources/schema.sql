CREATE TABLE IF NOT EXISTS members (
    id       UUID         PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    age      INTEGER      NOT NULL DEFAULT 0,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallets (
    id          UUID           PRIMARY KEY,
    member_id   UUID           NOT NULL UNIQUE REFERENCES members(id),
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    wallet_code VARCHAR(12)    NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS transactions (
    id             UUID           PRIMARY KEY,
    from_wallet_id UUID,
    to_wallet_id   UUID,
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    description    VARCHAR(255),
    created_at     TIMESTAMP      NOT NULL
);
