package com.modernize.bankbatch.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class PostJobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public PostJobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {

            // Reuse the pipeline-wide run.id so the completion update only counts
            // rows posted by the current pipeline execution.
            Timestamp runSince = new Timestamp(jobExecution.getJobParameters().getLong("run.id"));

            // Count after all partitions finish so record_count reflects the final
            // posted total once transaction inserts and staged status updates settle.
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions st " +
                "JOIN bank.transaction_batches tb ON tb.id = st.batch_id " +
                "JOIN bank.batch_jobs bj ON bj.id = tb.batch_job_id " +
                "WHERE st.status = 'posted' AND bj.started_at >= ?",
                Integer.class, runSince);

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'completed', finished_at = now(), record_count = ? " +
                "WHERE job_name = 'post_transactions' AND status = 'running'",
                count);

        } else {

            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'failed', finished_at = now() " +
                "WHERE job_name = 'post_transactions' AND status = 'running'");
        }
    }
}
