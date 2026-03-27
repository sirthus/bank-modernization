package com.modernize.bankbatch.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoadJobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public LoadJobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // setupBatchTasklet stored these ids in the execution context because the
        // listener runs after the chunk step, outside the tasklet's local scope.
        int batchJobId = jobExecution.getExecutionContext().getInt("batchJobId");
        int batchId = jobExecution.getExecutionContext().getInt("batchId");

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {

            // Count after the step completes so the job row reflects the final
            // persisted staged-record total for this inbound file.
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE batch_id = ?",
                Integer.class, batchId);

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'completed', finished_at = now(), record_count = ? " +
                "WHERE id = ?",
                count, batchJobId);

            jdbcTemplate.update(
                "UPDATE bank.transaction_batches SET record_count = ? WHERE id = ?",
                count, batchId);

        } else {

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'failed', finished_at = now() WHERE id = ?",
                batchJobId);
        }
    }
}
