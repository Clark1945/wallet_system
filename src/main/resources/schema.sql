CREATE TABLE IF NOT EXISTS members (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    age           INTEGER      NOT NULL DEFAULT 0,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(255),
    google_id     VARCHAR(255) UNIQUE,
    auth_provider VARCHAR(20)  NOT NULL DEFAULT 'LOCAL'
);

-- Migration for existing databases
ALTER TABLE members ADD COLUMN IF NOT EXISTS google_id     VARCHAR(255) UNIQUE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20)  NOT NULL DEFAULT 'LOCAL';
ALTER TABLE members ALTER COLUMN password DROP NOT NULL;

CREATE TABLE IF NOT EXISTS wallets (
    id          UUID           PRIMARY KEY,
    member_id   UUID           NOT NULL UNIQUE REFERENCES members(id),
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    wallet_code VARCHAR(12)    NOT NULL UNIQUE
);

-- 補上 wallet_code 欄位（給已存在的舊表格）
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS wallet_code VARCHAR(12) UNIQUE;

CREATE TABLE IF NOT EXISTS transactions (
    id             UUID           PRIMARY KEY,
    from_wallet_id UUID,
    to_wallet_id   UUID,
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    description    VARCHAR(255),
    created_at     TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tx_from_wallet ON transactions (from_wallet_id);
CREATE INDEX IF NOT EXISTS idx_tx_to_wallet   ON transactions (to_wallet_id);
CREATE INDEX IF NOT EXISTS idx_tx_created_at  ON transactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_type        ON transactions (type);

-- Profile fields migration
ALTER TABLE members ADD COLUMN IF NOT EXISTS nickname    VARCHAR(100);
ALTER TABLE members ADD COLUMN IF NOT EXISTS phone       VARCHAR(20);
ALTER TABLE members ADD COLUMN IF NOT EXISTS bio         VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS birthday    DATE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS avatar_path VARCHAR(255);

-- Withdrawal status migration
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS status VARCHAR(20);

-- Backfill: all existing transactions without status are treated as COMPLETED
UPDATE transactions
SET    status = 'COMPLETED'
WHERE  status IS NULL;

-- Member status, timestamps
ALTER TABLE members ADD COLUMN IF NOT EXISTS status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE members ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP    NOT NULL DEFAULT NOW();
ALTER TABLE members ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
