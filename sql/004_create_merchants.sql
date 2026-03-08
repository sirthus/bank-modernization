-- 004_create_merchants.sql

CREATE TABLE bank.merchants (
    merchant_id     INTEGER         NOT NULL PRIMARY KEY,
    name            TEXT            NOT NULL,
    category        TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
