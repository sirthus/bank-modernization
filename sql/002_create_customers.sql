-- 002_create_customers.sql

CREATE TABLE bank.customers (
    customer_id     INTEGER         NOT NULL PRIMARY KEY,
    full_name       TEXT            NOT NULL,
    email           TEXT            NOT NULL,
    phone           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
