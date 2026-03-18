package com.modernize.bankbatch.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class ValidateJobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public ValidateJobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {

            Timestamp runSince = new Timestamp(jobExecution.getJobParameters().getLong("run.id"));

            // Count validated + rejected records for the current run only
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions st " +
                "JOIN bank.transaction_batches tb ON tb.id = st.batch_id " +
                "JOIN bank.batch_jobs bj ON bj.id = tb.batch_job_id " +
                "WHERE st.status IN ('validated', 'rejected') AND bj.started_at >= ?",
                Integer.class, runSince);

            // Update the validation batch job row
            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'completed', finished_at = now(), record_count = ? " +
                "WHERE job_name = 'validate_transactions' AND status = 'running'",
                count);

        } else {

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'failed', finished_at = now() " +
                "WHERE job_name = 'validate_transactions' AND status = 'running'");
        }
    }
}
