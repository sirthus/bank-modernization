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
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {

            // Count how many staged records were loaded
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'staged'",
                Integer.class);

            // Update the batch job row
            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'completed', finished_at = now(), record_count = ? " +
                "WHERE status = 'running'",
                count);

            // Update the transaction batch row
            jdbcTemplate.update(
                "UPDATE bank.transaction_batches " +
                "SET record_count = ? " +
                "WHERE record_count IS NULL",
                count);

        } else {

            // Mark the job as failed
            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'failed', finished_at = now() " +
                "WHERE status = 'running'");
        }
    }
}
