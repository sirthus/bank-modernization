-- 015_validate_staged_transactions.sql
-- Validates staged transactions and flips status to 'validated' or 'rejected'.

DO $$
DECLARE
    v_job_id INTEGER;
    r        RECORD;
    v_valid  BOOLEAN;
    v_errors TEXT;
BEGIN

    -- Create a validation job
    INSERT INTO bank.batch_jobs (job_name, status)
    VALUES ('validate_transactions', 'running')
    RETURNING id INTO v_job_id;

    -- Loop through staged records
    FOR r IN
        SELECT st.id,
               st.account_id,
               st.direction,
               st.amount_cents,
               a.status AS account_status
          FROM bank.staged_transactions st
          LEFT JOIN bank.accounts a ON st.account_id = a.account_id
         WHERE st.status = 'staged'
    LOOP
        v_valid  := TRUE;
        v_errors := '';

        -- Rule 1: positive amount
        IF r.amount_cents <= 0 THEN
            v_valid  := FALSE;
            v_errors := v_errors || 'amount must be positive; ';
        END IF;

        -- Rule 2: valid direction
        IF r.direction NOT IN ('D', 'C') THEN
            v_valid  := FALSE;
            v_errors := v_errors || 'direction must be D or C; ';
        END IF;

        -- Rule 3: account exists
        IF r.account_status IS NULL THEN
            v_valid  := FALSE;
            v_errors := v_errors || 'account not found; ';
        END IF;

        -- Rule 4: account is active
        IF r.account_status IS NOT NULL AND r.account_status <> 'active' THEN
            v_valid  := FALSE;
            v_errors := v_errors || 'account is not active; ';
        END IF;

        -- Update status
        IF v_valid THEN
            UPDATE bank.staged_transactions
               SET status = 'validated'
             WHERE id = r.id;
        ELSE
            UPDATE bank.staged_transactions
               SET status = 'rejected'
             WHERE id = r.id;

            INSERT INTO bank.batch_job_errors (batch_job_id, error_message, record_ref)
            VALUES (v_job_id, v_errors, 'staged_transactions.id=' || r.id);
        END IF;

    END LOOP;

    -- Complete the job
    UPDATE bank.batch_jobs
       SET status = 'completed',
           finished_at = now(),
           record_count = (SELECT count(*) FROM bank.staged_transactions
                            WHERE status IN ('validated', 'rejected'))
     WHERE id = v_job_id;

END $$;