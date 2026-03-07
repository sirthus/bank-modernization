-- 017_post_validated_transactions.sql
-- Posts validated staged transactions into the production transactions table.

DO $$
DECLARE
    v_job_id      INTEGER;
    v_post_count  INTEGER;
BEGIN

    -- Create a posting job
    INSERT INTO bank.batch_jobs (job_name, status)
    VALUES ('post_transactions', 'running')
    RETURNING id INTO v_job_id;

    -- Insert validated staged records into production
    INSERT INTO bank.transactions (account_id, merchant_id, direction, amount_cents, status, description)
    SELECT account_id,
           merchant_id,
           direction,
           amount_cents,
           'posted',
           'Batch posted'
      FROM bank.staged_transactions
     WHERE status = 'validated';

    GET DIAGNOSTICS v_post_count = ROW_COUNT;

    -- Mark staged records as posted
    UPDATE bank.staged_transactions
       SET status = 'posted'
     WHERE status = 'validated';

    -- Complete the job
    UPDATE bank.batch_jobs
       SET status = 'completed',
           finished_at = now(),
           record_count = v_post_count
     WHERE id = v_job_id;

END $$;