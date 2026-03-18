-- Combined schema for Testcontainers integration tests.
-- Mirrors sql/001 through sql/014 in the correct dependency order.
-- 013_drop_staged_account_fk.sql is not needed: 010 already defines
-- account_id without a FK constraint.

-- 001
CREATE SCHEMA IF NOT EXISTS bank;

-- 002
CREATE TABLE bank.customers (
    customer_id     INTEGER         NOT NULL PRIMARY KEY,
    full_name       TEXT            NOT NULL,
    email           TEXT,
    phone           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 003
CREATE TABLE bank.accounts (
    account_id          INTEGER     NOT NULL PRIMARY KEY,
    customer_id         INTEGER     NOT NULL REFERENCES bank.customers(customer_id),
    account_type        TEXT        NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'active',
    opened_at           DATE        NOT NULL DEFAULT CURRENT_DATE,
    credit_limit_cents  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT accounts_account_type_check CHECK (account_type IN ('checking', 'savings', 'credit')),
    CONSTRAINT accounts_status_check CHECK (status IN ('active', 'frozen', 'closed')),
    CONSTRAINT accounts_credit_limit_cents_check CHECK (credit_limit_cents >= 0)
);

-- 004
CREATE TABLE bank.merchants (
    merchant_id     INTEGER         NOT NULL PRIMARY KEY,
    name            TEXT            NOT NULL,
    category        TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 005
CREATE TABLE bank.transactions (
    txn_id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      INTEGER         NOT NULL REFERENCES bank.accounts(account_id),
    merchant_id     INTEGER         REFERENCES bank.merchants(merchant_id),
    direction       CHAR(1)         NOT NULL,
    amount_cents    INTEGER         NOT NULL,
    status          TEXT            NOT NULL DEFAULT 'posted',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    description     TEXT,
    CONSTRAINT transactions_amount_cents_check CHECK (amount_cents > 0),
    CONSTRAINT transactions_direction_check CHECK (direction IN ('D', 'C')),
    CONSTRAINT transactions_status_check CHECK (status IN ('posted', 'pending', 'reversed'))
);

-- 006
CREATE INDEX accounts_customer_id_idx    ON bank.accounts (customer_id);
CREATE INDEX transactions_account_id_idx ON bank.transactions (account_id);
CREATE INDEX transactions_merchant_id_idx ON bank.transactions (merchant_id);

-- 007
CREATE TABLE bank.batch_jobs (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name        VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    record_count    INTEGER,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 008
CREATE TABLE bank.transaction_batches (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_job_id    INTEGER         NOT NULL REFERENCES bank.batch_jobs(id),
    file_name       VARCHAR(255)    NOT NULL,
    record_count    INTEGER,
    status          VARCHAR(20)     NOT NULL DEFAULT 'received',
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 009
CREATE TABLE bank.batch_job_errors (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_job_id    INTEGER         NOT NULL REFERENCES bank.batch_jobs(id),
    error_message   TEXT            NOT NULL,
    record_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 010 (no FK on account_id — unknown accounts must reach the validate job)
CREATE TABLE bank.staged_transactions (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        INTEGER         NOT NULL REFERENCES bank.transaction_batches(id),
    account_id      INTEGER         NOT NULL,
    merchant_id     INTEGER         REFERENCES bank.merchants(merchant_id),
    direction       CHAR(1)         NOT NULL,
    amount_cents    INTEGER         NOT NULL,
    txn_date        DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'staged',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 011
CREATE TABLE bank.batch_reconciliations (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_job_id        INTEGER         NOT NULL REFERENCES bank.batch_jobs(id),
    batch_id            INTEGER         NOT NULL REFERENCES bank.transaction_batches(id),
    staged_count        INTEGER         NOT NULL,
    posted_count        INTEGER         NOT NULL,
    staged_total_cents  BIGINT          NOT NULL,
    posted_total_cents  BIGINT          NOT NULL,
    counts_match        BOOLEAN         NOT NULL,
    totals_match        BOOLEAN         NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 014
ALTER TABLE bank.transactions
    ADD COLUMN batch_id INTEGER REFERENCES bank.transaction_batches(id);
