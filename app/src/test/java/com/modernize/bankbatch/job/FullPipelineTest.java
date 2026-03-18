package com.modernize.bankbatch.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the full pipeline:
 * Load -> Validate -> Post -> Reconcile.
 *
 * Unlike the per-job tests, no data is pre-inserted. The load job reads
 * a real CSV from the classpath, and each subsequent job consumes what the
 * previous job produced. This test verifies the hand-offs between jobs and
 * the final state across all four tables.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("batchtest")
class FullPipelineTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("loadTransactionsJob")
    private Job loadTransactionsJob;

    @Autowired
    @Qualifier("validateTransactionsJob")
    private Job validateTransactionsJob;

    @Autowired
    @Qualifier("postTransactionsJob")
    private Job postTransactionsJob;

    @Autowired
    @Qualifier("reconcileJob")
    private Job reconcileJob;

    @BeforeEach
    void cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions();

        // Delete in FK-safe order: children before parents.
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Runs all four jobs in pipeline order using a shared run.id so each job
     * can scope its work to this run. Asserts each job completes before
     * proceeding to the next.
     */
    private long runFullPipeline(String fileName) throws Exception {
        long runId = System.currentTimeMillis();

        jobLauncherTestUtils.setJob(loadTransactionsJob);
        JobExecution load = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("fileName", fileName)
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(load.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(validateTransactionsJob);
        JobExecution validate = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(validate.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(postTransactionsJob);
        JobExecution post = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(post.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(reconcileJob);
        JobExecution reconcile = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(reconcile.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        return runId;
    }

    // -------------------------------------------------------------------------
    // Reconciliation escalation
    // -------------------------------------------------------------------------

    /**
     * Simulates a posting failure by deleting a transaction after the post job
     * runs. The reconcile job records the mismatch and completes. Then verifies
     * that BatchPipelineService's check query detects the failure — proving the
     * service would throw rather than generating a summary report.
     */
    @Test
    void fullPipeline_transactionDeletedAfterPost_reconciliationFailureIsDetected() throws Exception {
        long runId = System.currentTimeMillis();

        // Load
        jobLauncherTestUtils.setJob(loadTransactionsJob);
        JobExecution load = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("fileName", "test_all_valid.csv")
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(load.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Validate
        jobLauncherTestUtils.setJob(validateTransactionsJob);
        JobExecution validate = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(validate.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Post
        jobLauncherTestUtils.setJob(postTransactionsJob);
        JobExecution post = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(post.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Simulate a posting failure: delete one transaction after the fact
        jdbcTemplate.update(
                "DELETE FROM bank.transactions WHERE txn_id = " +
                "(SELECT min(txn_id) FROM bank.transactions)");

        // Reconcile — records the mismatch but still completes (audit rows preserved)
        jobLauncherTestUtils.setJob(reconcileJob);
        JobExecution reconcile = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertThat(reconcile.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Reconciliation failure is recorded with correct counts
        Map<String, Object> recon = jdbcTemplate.queryForMap(
                "SELECT staged_count, posted_count, counts_match, totals_match " +
                "FROM bank.batch_reconciliations");
        assertThat(recon.get("staged_count")).isEqualTo(3);
        assertThat(recon.get("posted_count")).isEqualTo(2);
        assertThat(recon.get("counts_match")).isEqualTo(false);
        assertThat(recon.get("totals_match")).isEqualTo(false);

        // The same query BatchPipelineService uses returns > 0, proving the
        // service would throw rather than printing the summary report
        Integer failures = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_reconciliations br " +
                "JOIN bank.batch_jobs bj ON bj.id = br.batch_job_id " +
                "WHERE bj.started_at >= ? " +
                "  AND (NOT br.counts_match OR NOT br.totals_match)",
                Integer.class, new Timestamp(runId));
        assertThat(failures).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // All-valid file: full happy path
    // -------------------------------------------------------------------------

    /**
     * test_all_valid.csv — 3 records, all valid:
     *   2001  D  5000
     *   2002  C  1500
     *   2003  C  25000
     *   Total: 31500 cents
     */
    @Test
    void fullPipeline_allValidFile_allRecordsPostedAndReconciled() throws Exception {
        runFullPipeline("test_all_valid.csv");

        // staged_transactions: all 3 loaded, all 3 posted, none rejected
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'posted'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'", Integer.class))
                .isEqualTo(0);

        // transactions: all 3 present with correct total
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.transactions", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sum(amount_cents) FROM bank.transactions", Long.class))
                .isEqualTo(31500L);

        // batch_job_errors: none
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_job_errors", Integer.class))
                .isEqualTo(0);

        // reconciliation: PASS
        Map<String, Object> recon = jdbcTemplate.queryForMap(
                "SELECT staged_count, posted_count, staged_total_cents, " +
                "posted_total_cents, counts_match, totals_match " +
                "FROM bank.batch_reconciliations");
        assertThat(recon.get("staged_count")).isEqualTo(3);
        assertThat(recon.get("posted_count")).isEqualTo(3);
        assertThat(recon.get("staged_total_cents")).isEqualTo(31500L);
        assertThat(recon.get("posted_total_cents")).isEqualTo(31500L);
        assertThat(recon.get("counts_match")).isEqualTo(true);
        assertThat(recon.get("totals_match")).isEqualTo(true);
    }

    // -------------------------------------------------------------------------
    // Mixed file: valid and invalid records
    // -------------------------------------------------------------------------

    /**
     * test_mixed.csv — 5 records, 2 valid, 3 rejected:
     *   2001  D  5000     valid
     *   2002  D     0     rejected — amount must be positive (Rule 1)
     *   2003  C  1500     valid
     *   9999  D   750     rejected — account not found (Rule 3)
     *   2001  X   200     rejected — direction must be D or C (Rule 2)
     *   Posted total: 6500 cents
     */
    @Test
    void fullPipeline_mixedFile_correctSplitPostedAndReconciled() throws Exception {
        runFullPipeline("test_mixed.csv");

        // staged_transactions: all 5 loaded
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions", Integer.class))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'posted'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'", Integer.class))
                .isEqualTo(3);

        // transactions: only the 2 valid records, correct total
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.transactions", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sum(amount_cents) FROM bank.transactions", Long.class))
                .isEqualTo(6500L);

        // batch_job_errors: one error row per rejected record
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_job_errors", Integer.class))
                .isEqualTo(3);

        // reconciliation: PASS — rejected records are excluded from staged_count
        Map<String, Object> recon = jdbcTemplate.queryForMap(
                "SELECT staged_count, posted_count, staged_total_cents, " +
                "posted_total_cents, counts_match, totals_match " +
                "FROM bank.batch_reconciliations");
        assertThat(recon.get("staged_count")).isEqualTo(2);
        assertThat(recon.get("posted_count")).isEqualTo(2);
        assertThat(recon.get("staged_total_cents")).isEqualTo(6500L);
        assertThat(recon.get("posted_total_cents")).isEqualTo(6500L);
        assertThat(recon.get("counts_match")).isEqualTo(true);
        assertThat(recon.get("totals_match")).isEqualTo(true);
    }
}
