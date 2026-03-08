-- 003_create_accounts.sql

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
