package com.modernize.bankbatch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoadJobCompletionListener.
 *
 * Pure unit test — JdbcTemplate mocked, Spring context not loaded.
 * JobExecution is constructed directly using Spring Batch domain objects.
 *
 * The listener updates batch_jobs and transaction_batches on COMPLETED status,
 * and marks batch_jobs as failed on any other status.
 */
class LoadJobCompletionListenerTest {

    private JdbcTemplate jdbcTemplate;
    private LoadJobCompletionListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        listener = new LoadJobCompletionListener(jdbcTemplate);
    }

    // Helper: builds a JobExecution with batchJobId and batchId in context
    private JobExecution jobExecutionWithContext(BatchStatus status, int batchJobId, int batchId) {
        JobExecution execution = new JobExecution(
            new JobInstance(1L, "loadTransactionsJob"), new JobParameters());
        execution.setStatus(status);
        execution.getExecutionContext().putInt("batchJobId", batchJobId);
        execution.getExecutionContext().putInt("batchId", batchId);
        return execution;
    }

    /**
     * COMPLETED status: listener should query the staged count and update
     * both batch_jobs and transaction_batches.
     */
    @Test
    void afterJob_completedStatus_updatesBothTables() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(42);

        listener.afterJob(jobExecutionWithContext(BatchStatus.COMPLETED, 10, 20));

        // Should update batch_jobs (completed) and transaction_batches
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    /**
     * FAILED status: listener should mark the batch_jobs row as failed.
     * No update to transaction_batches (record count is irrelevant on failure).
     */
    @Test
    void afterJob_failedStatus_marksBatchJobFailed() {
        listener.afterJob(jobExecutionWithContext(BatchStatus.FAILED, 10, 20));

        // Only one update call (batch_jobs status=failed)
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
        // No count query issued on failure
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any());
    }
}
