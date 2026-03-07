-- 013_create_staged_transactions.sql
-- Holds inbound transactions before validation and posting.

CREATE TABLE bank.staged_transactions (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        INTEGER         NOT NULL REFERENCES bank.transaction_batches(id),
    account_id      INTEGER         NOT NULL REFERENCES bank.accounts(account_id),
    merchant_id     INTEGER         REFERENCES bank.merchants(merchant_id),
    direction       CHAR(1)         NOT NULL,
    amount_cents    INTEGER         NOT NULL,
    txn_date        DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'staged',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);