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
 * Unit tests for PostJobCompletionListener.
 *
 * Pure unit test — JdbcTemplate mocked. The listener updates the
 * post_transactions batch_jobs row on completion or failure.
 */
class PostJobCompletionListenerTest {

    private JdbcTemplate jdbcTemplate;
    private PostJobCompletionListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        listener = new PostJobCompletionListener(jdbcTemplate);
    }

    private JobExecution jobExecution(BatchStatus status) {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = new JobExecution(
            new JobInstance(3L, "postTransactionsJob"), params);
        execution.setStatus(status);
        return execution;
    }

    /**
     * COMPLETED: listener queries posted count, then updates post_transactions
     * batch_jobs row to completed with the record count.
     */
    @Test
    void afterJob_completedStatus_updatesJobToCompleted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(90);

        listener.afterJob(jobExecution(BatchStatus.COMPLETED));

        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Integer.class), any());
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    /**
     * FAILED: listener marks the post_transactions job as failed.
     * No count query is issued on failure.
     */
    @Test
    void afterJob_failedStatus_updatesJobToFailed() {
        listener.afterJob(jobExecution(BatchStatus.FAILED));

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any());
        verify(jdbcTemplate, times(1)).update(anyString());
    }
}
