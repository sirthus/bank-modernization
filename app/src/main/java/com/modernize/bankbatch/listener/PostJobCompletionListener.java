package com.modernize.bankbatch.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostJobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public PostJobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {

            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'posted'",
                Integer.class);

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
