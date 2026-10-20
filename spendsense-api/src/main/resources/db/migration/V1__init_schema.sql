-- V1__init_schema.sql
-- Initial schema for SpendSense

-- ──────────────────────────────────────────────
-- users
-- ──────────────────────────────────────────────
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- ──────────────────────────────────────────────
-- statements
-- ──────────────────────────────────────────────
CREATE TABLE statements (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename    VARCHAR(255),
    bank_type   VARCHAR(50)  DEFAULT 'REGIONS',
    uploaded_at TIMESTAMP    DEFAULT NOW(),
    status      VARCHAR(20)  DEFAULT 'PENDING'
    -- status values: PENDING | PARSED | CATEGORIZED | FAILED
);

-- ──────────────────────────────────────────────
-- transactions
-- ──────────────────────────────────────────────
CREATE TABLE transactions (
    id            BIGSERIAL PRIMARY KEY,
    statement_id  BIGINT  NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    date          DATE,
    description   TEXT,
    amount        NUMERIC(12, 2),
    type          VARCHAR(10),
    ai_category   VARCHAR(50),
    user_category VARCHAR(50),
    is_overridden BOOLEAN   DEFAULT FALSE,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- ──────────────────────────────────────────────
-- category_feedback
-- ──────────────────────────────────────────────
CREATE TABLE category_feedback (
    id                 BIGSERIAL PRIMARY KEY,
    transaction_id     BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    original_category  VARCHAR(50),
    corrected_category VARCHAR(50),
    created_at         TIMESTAMP DEFAULT NOW()
);
