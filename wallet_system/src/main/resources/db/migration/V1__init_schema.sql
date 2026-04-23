CREATE TABLE members (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    age            INTEGER      NOT NULL DEFAULT 0,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255),
    google_id      VARCHAR(255) UNIQUE,
    auth_provider  VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    nickname       VARCHAR(100),
    phone          VARCHAR(20),
    bio            VARCHAR(255),
    birthday       DATE,
    avatar_path    VARCHAR(255),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at  TIMESTAMP
);

CREATE TABLE wallets (
    id          UUID           PRIMARY KEY,
    member_id   UUID           NOT NULL UNIQUE REFERENCES members(id),
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    wallet_code VARCHAR(12)    NOT NULL UNIQUE
);

CREATE TABLE transactions (
    id                  UUID           PRIMARY KEY,
    from_wallet_id      UUID,
    to_wallet_id        UUID,
    type                VARCHAR(20)    NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    description         VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL,
    status              VARCHAR(20),
    payment_external_id VARCHAR(255)   UNIQUE
);

CREATE INDEX idx_tx_from_wallet ON transactions (from_wallet_id);
CREATE INDEX idx_tx_to_wallet   ON transactions (to_wallet_id);
CREATE INDEX idx_tx_created_at  ON transactions (created_at DESC);
CREATE INDEX idx_tx_type        ON transactions (type);
