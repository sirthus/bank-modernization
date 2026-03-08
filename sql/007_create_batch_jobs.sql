-- 007_create_batch_jobs.sql
-- Tracks each execution of a batch process.

CREATE TABLE bank.batch_jobs (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name        VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    record_count    INTEGER,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
