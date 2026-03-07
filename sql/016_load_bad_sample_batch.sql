-- 016_load_bad_sample_batch.sql
-- Loads sample-data/ach_20250308_bad.csv to test validation rejections.

DO $$
DECLARE
    v_job_id   INTEGER;
    v_batch_id INTEGER;
BEGIN

    -- Create the batch job
    INSERT INTO bank.batch_jobs (job_name, status)
    VALUES ('load_transactions', 'running')
    RETURNING id INTO v_job_id;

    -- Create the transaction batch
    INSERT INTO bank.transaction_batches (batch_job_id, file_name, status)
    VALUES (v_job_id, 'ach_20250308_bad.csv', 'received')
    RETURNING id INTO v_batch_id;

    -- Insert staged transactions (mix of good and bad)
    INSERT INTO bank.staged_transactions
        (batch_id, account_id, merchant_id, direction, amount_cents, txn_date)
    VALUES
        (v_batch_id, 2001, 3001, 'D',  1500,  '2025-03-08'),
        (v_batch_id, 2002, 3002, 'D',  -200,  '2025-03-08'),
        (v_batch_id, 2003, NULL, 'C',  0,     '2025-03-08'),
        (v_batch_id, 2001, 3001, 'X',  3000,  '2025-03-08'),
        (v_batch_id, 2002, NULL, 'C',  7500,  '2025-03-08');

    -- Update the batch and job
    UPDATE bank.transaction_batches
       SET record_count = 5,
           status = 'received'
     WHERE id = v_batch_id;

    UPDATE bank.batch_jobs
       SET status = 'completed',
           finished_at = now(),
           record_count = 5
     WHERE id = v_job_id;

END $$;
