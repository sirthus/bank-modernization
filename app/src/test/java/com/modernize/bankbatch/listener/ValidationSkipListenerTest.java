package com.modernize.bankbatch.listener;

import com.modernize.bankbatch.exception.ValidationException;
import com.modernize.bankbatch.model.StagedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidationSkipListener.
 *
 * Pure unit test — JdbcTemplate mocked. The listener is responsible for:
 *   1. Marking rejected records with status = 'rejected' in staged_transactions.
 *   2. Writing an error row to batch_job_errors (only when validateJobId is set).
 *
 * This is the listener that Spring Batch calls when ValidationProcessor throws
 * a ValidationException and the skip policy absorbs it. It owns the persistence
 * side of the rejection path.
 */
class ValidationSkipListenerTest {

    private JdbcTemplate jdbcTemplate;
    private ValidationSkipListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        listener = new ValidationSkipListener(jdbcTemplate);
    }

    // Helper: builds a staged transaction that failed validation
    private StagedTransaction rejectedItem(int id) {
        StagedTransaction txn = new StagedTransaction();
        txn.setId(id);
        txn.setAmountCents(0);
        txn.setDirection("X");
        return txn;
    }

    /**
     * Normal skip: listener updates staged_transactions to 'rejected' and
     * inserts an error row in batch_job_errors when validateJobId is set.
     */
    @Test
    void onSkipInProcess_withValidateJobId_updatesStatusAndInsertsError() {
        listener.setValidateJobId(new AtomicInteger(99));

        ValidationException ex = new ValidationException(1, "amount must be positive");
        listener.onSkipInProcess(rejectedItem(1), ex);

        // Status update
        verify(jdbcTemplate, times(1))
            .update(contains("status = 'rejected'"), eq(1));

        // Error insert
        verify(jdbcTemplate, times(1))
            .update(contains("batch_job_errors"), eq(99), anyString(), anyString());
    }

    /**
     * No validateJobId: listener still marks the record rejected but skips
     * the error insert (no job ID to link against).
     *
     * This covers the edge case where the validate setup tasklet did not set
     * the job ID before skips started occurring.
     */
    @Test
    void onSkipInProcess_withoutValidateJobId_updatesStatusButSkipsErrorInsert() {
        // validateJobId is null (never set)
        ValidationException ex = new ValidationException(2, "direction must be D or C");
        listener.onSkipInProcess(rejectedItem(2), ex);

        // Status update must still happen
        verify(jdbcTemplate, times(1))
            .update(contains("status = 'rejected'"), eq(2));

        // Error insert must NOT happen
        verify(jdbcTemplate, never())
            .update(contains("batch_job_errors"), any(), any(), any());
    }
}
