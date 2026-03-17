package com.modernize.bankbatch.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("batchtest")
class ReconcileJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("reconcileJob")
    private Job reconcileJob;

    @BeforeEach
    void cleanUp() {
        jobLauncherTestUtils.setJob(reconcileJob);
        jobRepositoryTestUtils.removeJobExecutions();

        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    private int insertBatch(long runId) {
        int jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status, started_at) " +
                "VALUES ('load_transactions', 'completed', ?) RETURNING id",
                Integer.class, new java.sql.Timestamp(runId));
        return jdbcTemplate.queryForObject(
                "INSERT INTO bank.transaction_batches (batch_job_id, file_name, status) " +
                "VALUES (?, 'test.csv', 'received') RETURNING id",
                Integer.class, jobId);
    }

    JobExecution getJobExecution() {
        jobLauncherTestUtils.setJob(reconcileJob);
        return MetaDataInstanceFactory.createJobExecution();
    }

    private JobExecution launchReconcileJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    @Test
    void reconcileJob_postedBatch_writesMatchingCountsAndTotals() throws Exception {
        long runId = System.currentTimeMillis();
        int batchId = insertBatch(runId);

        jdbcTemplate.update(
                "INSERT INTO bank.staged_transactions " +
                "(batch_id, account_id, direction, amount_cents, txn_date, status) " +
                "VALUES (?, 2001, 'D', 5000, '2025-03-10', 'posted')",
                batchId);
        jdbcTemplate.update(
                "INSERT INTO bank.staged_transactions " +
                "(batch_id, account_id, direction, amount_cents, txn_date, status) " +
                "VALUES (?, 2002, 'C', 1500, '2025-03-10', 'posted')",
                batchId);

        jdbcTemplate.update(
                "INSERT INTO bank.transactions " +
                "(account_id, direction, amount_cents, status, description, batch_id) " +
                "VALUES (2001, 'D', 5000, 'posted', 'Batch posted', ?)",
                batchId);
        jdbcTemplate.update(
                "INSERT INTO bank.transactions " +
                "(account_id, direction, amount_cents, status, description, batch_id) " +
                "VALUES (2002, 'C', 1500, 'posted', 'Batch posted', ?)",
                batchId);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("run.id", runId)
                        .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Map<String, Object> recon = jdbcTemplate.queryForMap(
                "SELECT batch_id, staged_count, posted_count, staged_total_cents, " +
                "posted_total_cents, counts_match, totals_match " +
                "FROM bank.batch_reconciliations");

        assertThat(recon.get("batch_id")).isEqualTo(batchId);
        assertThat(recon.get("staged_count")).isEqualTo(2);
        assertThat(recon.get("posted_count")).isEqualTo(2);
        assertThat(recon.get("staged_total_cents")).isEqualTo(6500L);
        assertThat(recon.get("posted_total_cents")).isEqualTo(6500L);
        assertThat(recon.get("counts_match")).isEqualTo(true);
        assertThat(recon.get("totals_match")).isEqualTo(true);
    }
}
