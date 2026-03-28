package com.modernize.bankbatch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconcileJobCompletionListener.
 *
 * Pure unit test — JdbcTemplate mocked.
 *
 * This listener is unusual: it only acts on non-COMPLETED status. When the
 * reconcile job succeeds, the tasklet itself writes the completion record;
 * the listener only closes out failed runs so the audit trail is clean.
 */
class ReconcileJobCompletionListenerTest {

    private JdbcTemplate jdbcTemplate;
    private ReconcileJobCompletionListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        listener = new ReconcileJobCompletionListener(jdbcTemplate);
    }

    private JobExecution jobExecution(BatchStatus status) {
        JobExecution execution = new JobExecution(
            new JobInstance(4L, "reconcileJob"), new JobParameters());
        execution.setStatus(status);
        return execution;
    }

    /**
     * COMPLETED: the tasklet already wrote the completion record. The listener
     * must do nothing — no DB calls.
     */
    @Test
    void afterJob_completedStatus_doesNothing() {
        listener.afterJob(jobExecution(BatchStatus.COMPLETED));

        verify(jdbcTemplate, never()).update(anyString());
    }

    /**
     * FAILED: listener must mark the reconcile_batches job row as failed so
     * the audit trail does not leave a dangling 'running' status.
     */
    @Test
    void afterJob_failedStatus_marksJobFailed() {
        listener.afterJob(jobExecution(BatchStatus.FAILED));

        verify(jdbcTemplate, times(1)).update(anyString());
    }

    /**
     * STOPPED: also a non-COMPLETED status, same failure path applies.
     */
    @Test
    void afterJob_stoppedStatus_marksJobFailed() {
        listener.afterJob(jobExecution(BatchStatus.STOPPED));

        verify(jdbcTemplate, times(1)).update(anyString());
    }
}
