-- 011_create_transaction_batches.sql
-- Represents a group of inbound transactions that arrived together.

CREATE TABLE bank.transaction_batches (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_job_id    INTEGER         NOT NULL REFERENCES bank.batch_jobs(id),
    file_name       VARCHAR(255)    NOT NULL,
    record_count    INTEGER,
    status          VARCHAR(20)     NOT NULL DEFAULT 'received',
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
