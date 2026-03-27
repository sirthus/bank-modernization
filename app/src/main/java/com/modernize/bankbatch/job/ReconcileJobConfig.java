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

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Configuration
/**
 * Configures reconciliation for the batches loaded in the current pipeline run.
 *
 * This job always writes reconciliation rows first; the pipeline service decides
 * afterward whether any mismatches should fail the overall run.
 */
public class ReconcileJobConfig {

    @Bean
    public Tasklet reconcileTasklet(JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {
            Long runId = ((Number) chunkContext.getStepContext()
                .getJobParameters()
                .get("run.id")).longValue();
            // run.id is created once in the pipeline service and reused across
            // jobs so reconciliation can scope itself to the current end-to-end run.
            Timestamp runSince = new Timestamp(runId);

            // Create the reconciliation job row
            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('reconcile_batches', 'running') RETURNING id",
                Integer.class);

            // Reconcile only batches loaded in the current pipeline run.
            List<Map<String, Object>> batches = jdbcTemplate.queryForList(
                "SELECT tb.id AS batch_id " +
                "FROM bank.transaction_batches tb " +
                "JOIN bank.batch_jobs bj ON bj.id = tb.batch_job_id " +
                "WHERE bj.job_name = 'load_transactions' " +
                "  AND bj.started_at >= ? " +
                "  AND EXISTS ( " +
                "      SELECT 1 FROM bank.staged_transactions st " +
                "      WHERE st.batch_id = tb.id AND st.status = 'posted') " +
                "ORDER BY tb.id",
                runSince);

            for (Map<String, Object> row : batches) {
                Integer batchId = (Integer) row.get("batch_id");

                // Count and sum from staged
                Map<String, Object> staged = jdbcTemplate.queryForMap(
                    "SELECT count(*) AS cnt, coalesce(sum(amount_cents), 0) AS total " +
                    "FROM bank.staged_transactions " +
                    "WHERE batch_id = ? AND status = 'posted'",
                    batchId);

                // Count and sum from production scoped to this batch_id
                Map<String, Object> posted = jdbcTemplate.queryForMap(
                    "SELECT count(*) AS cnt, coalesce(sum(amount_cents), 0) AS total " +
                    "FROM bank.transactions " +
                    "WHERE batch_id = ?",
                    batchId);

                long stagedCount = (Long) staged.get("cnt");
                long stagedTotal = (Long) staged.get("total");
                long postedCount = (Long) posted.get("cnt");
                long postedTotal = (Long) posted.get("total");

                // Always persist the audit row, even for mismatches. The pipeline
                // service checks these rows later and turns mismatches into a failure.
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
