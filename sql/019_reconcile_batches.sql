-- 019_reconcile_batches.sql
-- Compares staged posted records against production for each batch.

DO $$
DECLARE
    v_job_id            INTEGER;
    r                   RECORD;
    v_staged_count      INTEGER;
    v_posted_count      INTEGER;
    v_staged_total      BIGINT;
    v_posted_total      BIGINT;
BEGIN

    -- Create a reconciliation job
    INSERT INTO bank.batch_jobs (job_name, status)
    VALUES ('reconcile_batches', 'running')
    RETURNING id INTO v_job_id;

    -- Loop through each batch that has posted records
    FOR r IN
        SELECT DISTINCT batch_id
          FROM bank.staged_transactions
         WHERE status = 'posted'
    LOOP

        -- Count and sum from staged
        SELECT count(*), coalesce(sum(amount_cents), 0)
          INTO v_staged_count, v_staged_total
          FROM bank.staged_transactions
         WHERE batch_id = r.batch_id
           AND status = 'posted';

        -- Count and sum from production
        -- Match on account_id, merchant_id, direction, amount_cents
        SELECT count(*), coalesce(sum(t.amount_cents), 0)
          INTO v_posted_count, v_posted_total
          FROM bank.staged_transactions st
          JOIN bank.transactions t
            ON t.account_id  = st.account_id
           AND t.amount_cents = st.amount_cents
           AND t.direction    = st.direction
           AND coalesce(t.merchant_id, -1) = coalesce(st.merchant_id, -1)
           AND t.description  = 'Batch posted'
         WHERE st.batch_id = r.batch_id
           AND st.status = 'posted';

        -- Record the result
        INSERT INTO bank.batch_reconciliations
            (batch_job_id, batch_id, staged_count, posted_count,
             staged_total_cents, posted_total_cents, counts_match, totals_match)
        VALUES
            (v_job_id, r.batch_id, v_staged_count, v_posted_count,
             v_staged_total, v_posted_total,
             v_staged_count = v_posted_count,
             v_staged_total = v_posted_total);

    END LOOP;

    -- Complete the job
    UPDATE bank.batch_jobs
       SET status = 'completed',
           finished_at = now(),
           record_count = (SELECT count(*) FROM bank.batch_reconciliations
                            WHERE batch_job_id = v_job_id)
     WHERE id = v_job_id;

END $$;