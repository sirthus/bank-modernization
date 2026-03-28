package com.modernize.bankbatch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidateJobCompletionListener.
 *
 * Pure unit test — JdbcTemplate mocked. The listener updates the
 * validate_transactions batch_jobs row on completion or failure.
 */
class ValidateJobCompletionListenerTest {

    private JdbcTemplate jdbcTemplate;
    private ValidateJobCompletionListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        listener = new ValidateJobCompletionListener(jdbcTemplate);
    }

    // Helper: builds a JobExecution with run.id in parameters (required by the listener)
    private JobExecution jobExecution(BatchStatus status) {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = new JobExecution(
            new JobInstance(2L, "validateTransactionsJob"), params);
        execution.setStatus(status);
        return execution;
    }

    /**
     * COMPLETED: listener queries validated/rejected count, then updates
     * the validate_transactions batch_jobs row to completed.
     */
    @Test
    void afterJob_completedStatus_updatesJobToCompleted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(150);

        listener.afterJob(jobExecution(BatchStatus.COMPLETED));

        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Integer.class), any());
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    /**
     * FAILED: listener marks the validate_transactions job as failed.
     * No count query is issued — record count is irrelevant on failure.
     */
    @Test
    void afterJob_failedStatus_updatesJobToFailed() {
        listener.afterJob(jobExecution(BatchStatus.FAILED));

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any());
        // Failure path uses update(String) with no args
        verify(jdbcTemplate, times(1)).update(anyString());
    }
}
