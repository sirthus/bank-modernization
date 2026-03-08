-- 005_create_transactions.sql

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
