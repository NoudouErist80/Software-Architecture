-- VIBE Auth Service — Database Migration V1
-- Creates the core users table
-- Author: TCHANGO NOUDOU JOSEPH

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(50)  NOT NULL,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(100) NOT NULL,
    phone_number        VARCHAR(20),
    country_code        VARCHAR(5)   NOT NULL DEFAULT 'CM',
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER',
    preferred_language  VARCHAR(10)  NOT NULL DEFAULT 'fr',
    is_active           BOOLEAN      NOT NULL DEFAULT true,
    is_email_verified   BOOLEAN      NOT NULL DEFAULT false,
    is_phone_verified   BOOLEAN      NOT NULL DEFAULT false,
    profile_picture_url TEXT,
    last_login_at       TIMESTAMPTZ,
    streak_days         INT          NOT NULL DEFAULT 0,
    last_streak_date    TIMESTAMPTZ,
    failed_login_attempts INT        NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE INDEX idx_users_email       ON users(email);
CREATE INDEX idx_users_username    ON users(username);
CREATE INDEX idx_users_phone       ON users(phone_number);
CREATE INDEX idx_users_country     ON users(country_code);
CREATE INDEX idx_users_created_at  ON users(created_at DESC);

COMMENT ON TABLE users IS 'VIBE platform users — authentication and identity';
COMMENT ON COLUMN users.preferred_language IS 'BCP-47 language code: en, fr, ha, ewo, pcm, yo, tw, fat, gaa';
COMMENT ON COLUMN users.streak_days IS 'Consecutive daily login days for streak bonus tokens';
