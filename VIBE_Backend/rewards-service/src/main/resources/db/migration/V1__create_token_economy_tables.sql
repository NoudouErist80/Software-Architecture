-- VIBE Rewards Service — Database Migration V1
-- Creates the complete token economy schema
-- Author: TCHANGO NOUDOU JOSEPH

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Token Wallets ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS token_wallets (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL,
    balance                 BIGINT      NOT NULL DEFAULT 0,
    total_earned_lifetime   BIGINT      NOT NULL DEFAULT 0,
    total_redeemed_lifetime BIGINT      NOT NULL DEFAULT 0,
    today_earned            INT         NOT NULL DEFAULT 0,
    today_date              TIMESTAMPTZ,
    week_earned             INT         NOT NULL DEFAULT 0,
    month_earned            INT         NOT NULL DEFAULT 0,
    is_frozen               BOOLEAN     NOT NULL DEFAULT false,
    freeze_reason           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_wallet_user UNIQUE (user_id),
    CONSTRAINT chk_balance_positive CHECK (balance >= 0)
);

CREATE INDEX idx_wallet_user        ON token_wallets(user_id);
CREATE INDEX idx_wallet_week_earned ON token_wallets(week_earned DESC);

-- ── Token Transactions (full audit log) ───────────────────────────────
CREATE TABLE IF NOT EXISTS token_transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    amount           INT         NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    earn_type        VARCHAR(30),
    description      TEXT,
    reference_id     VARCHAR(255),
    balance_after    BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_user       ON token_transactions(user_id);
CREATE INDEX idx_txn_created    ON token_transactions(created_at DESC);
CREATE INDEX idx_txn_type       ON token_transactions(transaction_type);

-- ── Cashout Requests ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cashout_requests (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID           NOT NULL,
    tokens_amount      INT            NOT NULL,
    xaf_amount         DECIMAL(10,2)  NOT NULL,
    provider           VARCHAR(30)    NOT NULL,
    phone_number       VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    provider_reference VARCHAR(255),
    failure_reason     TEXT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    processed_at       TIMESTAMPTZ,

    CONSTRAINT chk_tokens_positive CHECK (tokens_amount > 0),
    CONSTRAINT chk_xaf_positive    CHECK (xaf_amount > 0)
);

CREATE INDEX idx_cashout_user    ON cashout_requests(user_id);
CREATE INDEX idx_cashout_status  ON cashout_requests(status);
CREATE INDEX idx_cashout_created ON cashout_requests(created_at DESC);

COMMENT ON TABLE token_wallets     IS 'VIBE token economy — one wallet per user';
COMMENT ON TABLE token_transactions IS 'Full audit log of every token earn and spend';
COMMENT ON TABLE cashout_requests   IS 'MTN MoMo / Orange Money cashout requests';
