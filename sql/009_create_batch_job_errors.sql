-- 009_create_batch_job_errors.sql
-- Captures errors that occur during a batch job run.

CREATE TABLE bank.batch_job_errors (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_job_id    INTEGER         NOT NULL REFERENCES bank.batch_jobs(id),
    error_message   TEXT            NOT NULL,
    record_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
