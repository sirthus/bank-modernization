-- 011_create_batch_reconciliations.sql
-- Stores reconciliation results comparing staged posts against production.

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
