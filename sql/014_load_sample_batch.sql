-- 014_load_sample_batch.sql
-- Simulates loading sample-data/ach_20250307.csv through the batch control flow.

DO $$
DECLARE
    v_job_id   INTEGER;
    v_batch_id INTEGER;
BEGIN

    -- Step 1: Create the batch job
    INSERT INTO bank.batch_jobs (job_name, status)
    VALUES ('load_transactions', 'running')
    RETURNING id INTO v_job_id;

    -- Step 2: Create the transaction batch
    INSERT INTO bank.transaction_batches (batch_job_id, file_name, status)
    VALUES (v_job_id, 'ach_20250307.csv', 'received')
    RETURNING id INTO v_batch_id;

    -- Step 3: Insert staged transactions
    INSERT INTO bank.staged_transactions
        (batch_id, account_id, merchant_id, direction, amount_cents, txn_date)
    VALUES
        (v_batch_id, 2001, 3001, 'D',  4250,   '2025-03-07'),
        (v_batch_id, 2001, 3002, 'D',  2800,   '2025-03-07'),
        (v_batch_id, 2002, 3001, 'D',  1575,   '2025-03-07'),
        (v_batch_id, 2003, NULL, 'C',  50000,  '2025-03-07'),
        (v_batch_id, 2002, 3002, 'D',  3100,   '2025-03-07'),
        (v_batch_id, 2001, NULL, 'C',  100000, '2025-03-07');

    -- Step 4: Update the batch and job
    UPDATE bank.transaction_batches
       SET record_count = 6,
           status = 'received'
     WHERE id = v_batch_id;

    UPDATE bank.batch_jobs
       SET status = 'completed',
           finished_at = now(),
           record_count = 6
     WHERE id = v_job_id;

END $$;