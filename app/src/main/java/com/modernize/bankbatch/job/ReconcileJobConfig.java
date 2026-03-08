package com.modernize.bankbatch.job;

import com.modernize.bankbatch.listener.ReconcileJobCompletionListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

@Configuration
public class ReconcileJobConfig {

    @Bean
    public Tasklet reconcileTasklet(JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {

            // Create the reconciliation job row
            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('reconcile_batches', 'running') RETURNING id",
                Integer.class);

            // Find all batches that have posted records
            List<Map<String, Object>> batches = jdbcTemplate.queryForList(
                "SELECT DISTINCT batch_id FROM bank.staged_transactions " +
                "WHERE status = 'posted'");

            for (Map<String, Object> row : batches) {
                Integer batchId = (Integer) row.get("batch_id");

                // Count and sum from staged
                Map<String, Object> staged = jdbcTemplate.queryForMap(
                    "SELECT count(*) AS cnt, coalesce(sum(amount_cents), 0) AS total " +
                    "FROM bank.staged_transactions " +
                    "WHERE batch_id = ? AND status = 'posted'",
                    batchId);

                // Count and sum from production by matching on key fields
                Map<String, Object> posted = jdbcTemplate.queryForMap(
                    "SELECT count(*) AS cnt, coalesce(sum(t.amount_cents), 0) AS total " +
                    "FROM bank.staged_transactions st " +
                    "JOIN bank.transactions t " +
                    "  ON t.account_id = st.account_id " +
                    " AND t.amount_cents = st.amount_cents " +
                    " AND t.direction = st.direction " +
                    " AND coalesce(t.merchant_id, -1) = coalesce(st.merchant_id, -1) " +
                    " AND t.description = 'Batch posted' " +
                    "WHERE st.batch_id = ? AND st.status = 'posted'",
                    batchId);

                long stagedCount = (Long) staged.get("cnt");
                long stagedTotal = (Long) staged.get("total");
                long postedCount = (Long) posted.get("cnt");
                long postedTotal = (Long) posted.get("total");

                // Record the reconciliation result
                jdbcTemplate.update(
                    "INSERT INTO bank.batch_reconciliations " +
                    "(batch_job_id, batch_id, staged_count, posted_count, " +
                    " staged_total_cents, posted_total_cents, counts_match, totals_match) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    jobId, batchId,
                    stagedCount, postedCount,
                    stagedTotal, postedTotal,
                    stagedCount == postedCount,
                    stagedTotal == postedTotal);
            }

            // Complete the job
            jdbcTemplate.update(
                "UPDATE bank.batch_jobs " +
                "SET status = 'completed', finished_at = now(), record_count = ? " +
                "WHERE id = ?",
                batches.size(), jobId);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step reconcileStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              Tasklet reconcileTasklet) {
        return new StepBuilder("reconcileStep", jobRepository)
                .tasklet(reconcileTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job reconcileJob(JobRepository jobRepository,
                            Step reconcileStep,
                            ReconcileJobCompletionListener listener) {
        return new JobBuilder("reconcileJob", jobRepository)
                .listener(listener)
                .start(reconcileStep)
                .build();
    }
}
