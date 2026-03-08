package com.modernize.bankbatch.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReconcileJobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public ReconcileJobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'failed', finished_at = now() " +
                "WHERE job_name = 'reconcile_batches' AND status = 'running'");
        }
    }
}
